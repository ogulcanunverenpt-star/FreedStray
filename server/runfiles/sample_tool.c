#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <sys/utsname.h>
#include <zlib.h>

#define RTSP_SERVER_URL "rtsp://192.168.1.9:8554/screen"
#define HTTP_HOST "raw.githubusercontent.com"
#define HTTP_PORT 80

// Cihazın CPU mimarisini tespit et
void detect_abi(char *abi_out, size_t max_len) {
    FILE *fp = popen("getprop ro.product.cpu.abi 2>/dev/null", "r");
    if (fp) {
        if (fgets(abi_out, max_len, fp) != NULL) {
            abi_out[strcspn(abi_out, "\r\n")] = '\0';
        }
        pclose(fp);
    }

    if (strlen(abi_out) == 0) {
        struct utsname buffer;
        if (uname(&buffer) == 0) {
            if (strcmp(buffer.machine, "aarch64") == 0) {
                snprintf(abi_out, max_len, "arm64-v8a");
            } else if (strstr(buffer.machine, "arm") != NULL) {
                snprintf(abi_out, max_len, "armeabi-v7a");
            } else if (strcmp(buffer.machine, "x86_64") == 0) {
                snprintf(abi_out, max_len, "x86_64");
            } else {
                snprintf(abi_out, max_len, "%s", buffer.machine);
            }
        }
    }

    if (strlen(abi_out) == 0) {
        snprintf(abi_out, max_len, "armeabi-v7a");
    }
}

// Saf C Soketi ile HTTP Üzerinden Dosya İndirme
int http_download_raw(const char *host, const char *path, const char *dest_file) {
    int sock;
    struct sockaddr_in server_addr;
    struct hostent *he;
    char request[2048];
    char buffer[16384];

    printf("[*] DNS Çözümleniyor: %s...\n", host);
    he = gethostbyname(host);
    if (!he) {
        perror("[!] DNS Çözümleme Hatası");
        return 0;
    }

    sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        perror("[!] Soket Oluşturulamadı");
        return 0;
    }

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(HTTP_PORT);
    memcpy(&server_addr.sin_addr, he->h_addr_list[0], he->h_length);

    printf("[*] Sunucuya Bağlanılıyor %s:%d...\n", inet_ntoa(server_addr.sin_addr), HTTP_PORT);
    if (connect(sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        perror("[!] Bağlantı Başarısız");
        close(sock);
        return 0;
    }

    snprintf(request, sizeof(request),
             "GET %s HTTP/1.1\r\n"
             "Host: %s\r\n"
             "User-Agent: PureCDownloader/1.0\r\n"
             "Connection: close\r\n\r\n",
             path, host);

    if (send(sock, request, strlen(request), 0) < 0) {
        perror("[!] HTTP İsteği Gönderilemedi");
        close(sock);
        return 0;
    }

    FILE *fp = fopen(dest_file, "wb");
    if (!fp) {
        perror("[!] Hedef Dosya Açılamadı");
        close(sock);
        return 0;
    }

    int bytes_received;
    int header_parsed = 0;

    while ((bytes_received = recv(sock, buffer, sizeof(buffer), 0)) > 0) {
        if (!header_parsed) {
            char *header_end = strstr(buffer, "\r\n\r\n");
            if (header_end) {
                header_end += 4;
                int header_len = header_end - buffer;
                int payload_len = bytes_received - header_len;
                if (payload_len > 0) {
                    fwrite(header_end, 1, payload_len, fp);
                }
                header_parsed = 1;
            }
        } else {
            fwrite(buffer, 1, bytes_received, fp);
        }
    }

    fclose(fp);
    close(sock);
    printf("[+] İndirme Tamamlandı: %s\n", dest_file);
    return 1;
}

// Saf C ve zlib kullanarak PKZIP arşivinden 'ffmpeg' dosyasını çıkarma
#pragma pack(push, 1)
typedef struct {
    uint32_t signature;
    uint16_t version;
    uint16_t flags;
    uint16_t compression;
    uint16_t mod_time;
    uint16_t mod_date;
    uint32_t crc32;
    uint32_t compressed_size;
    uint32_t uncompressed_size;
    uint16_t filename_len;
    uint16_t extra_len;
} ZipLocalHeader;
#pragma pack(pop)

int extract_ffmpeg_from_zip(const char *zip_file, const char *out_ffmpeg_file) {
    FILE *fp = fopen(zip_file, "rb");
    if (!fp) {
        perror("[!] Zip Dosyası Açılamadı");
        return 0;
    }

    ZipLocalHeader hdr;
    char filename_buf[512];

    while (fread(&hdr, sizeof(ZipLocalHeader), 1, fp) == 1) {
        if (hdr.signature != 0x04034b50) {
            break;
        }

        if (hdr.filename_len < sizeof(filename_buf)) {
            fread(filename_buf, 1, hdr.filename_len, fp);
            filename_buf[hdr.filename_len] = '\0';
        } else {
            fseek(fp, hdr.filename_len, SEEK_CUR);
            filename_buf[0] = '\0';
        }

        fseek(fp, hdr.extra_len, SEEK_CUR);

        if (strstr(filename_buf, "ffmpeg") != NULL && !strstr(filename_buf, "/")) {
            printf("[*] Zip İçinde 'ffmpeg' Bulundu (%u bytes). Extracting...\n", hdr.uncompressed_size);

            unsigned char *comp_data = (unsigned char *)malloc(hdr.compressed_size);
            unsigned char *uncomp_data = (unsigned char *)malloc(hdr.uncompressed_size);

            if (!comp_data || !uncomp_data) {
                printf("[!] Bellek Ayrılması Başarısız\n");
                if (comp_data) free(comp_data);
                if (uncomp_data) free(uncomp_data);
                fclose(fp);
                return 0;
            }

            fread(comp_data, 1, hdr.compressed_size, fp);

            if (hdr.compression == 0) {
                memcpy(uncomp_data, comp_data, hdr.compressed_size);
            } else if (hdr.compression == 8) {
                z_stream strm;
                memset(&strm, 0, sizeof(strm));
                strm.next_in = comp_data;
                strm.avail_in = hdr.compressed_size;
                strm.next_out = uncomp_data;
                strm.avail_out = hdr.uncompressed_size;

                if (inflateInit2(&strm, -MAX_WBITS) != Z_OK) {
                    printf("[!] zlib inflateInit2 Başarısız\n");
                    free(comp_data);
                    free(uncomp_data);
                    fclose(fp);
                    return 0;
                }

                int ret = inflate(&strm, Z_FINISH);
                inflateEnd(&strm);

                if (ret != Z_STREAM_END) {
                    printf("[!] Deflate Çözme Hatası: %d\n", ret);
                    free(comp_data);
                    free(uncomp_data);
                    fclose(fp);
                    return 0;
                }
            } else {
                printf("[!] Desteklenmeyen Sıkıştırma Yöntemi: %d\n", hdr.compression);
                free(comp_data);
                free(uncomp_data);
                fclose(fp);
                return 0;
            }

            FILE *out_fp = fopen(out_ffmpeg_file, "wb");
            if (out_fp) {
                fwrite(uncomp_data, 1, hdr.uncompressed_size, out_fp);
                fclose(out_fp);
                printf("[+] FFmpeg Çıkarıldı: %s\n", out_ffmpeg_file);
            }

            free(comp_data);
            free(uncomp_data);
            fclose(fp);
            return 1;
        } else {
            fseek(fp, hdr.compressed_size, SEEK_CUR);
        }
    }

    fclose(fp);
    printf("[!] Zip Arşivinde 'ffmpeg' Bulunamadı.\n");
    return 0;
}

// Android Ekranını Yakalayıp RTSP 8554 Portuna Canlı Yayınlama
void start_screen_rtsp_stream(const char *ffmpeg_path) {
    char cmd[2048];
    printf("\n=========================================\n");
    printf(" 🎥 Android Screen Streamer -> RTSP 8554 \n");
    printf("=========================================\n");
    printf("[*] FFmpeg Binary: %s\n", ffmpeg_path);
    printf("[*] RTSP Stream Target: %s\n", RTSP_SERVER_URL);
    fflush(stdout);

    chmod(ffmpeg_path, S_IRWXU | S_IRWXG | S_IRWXO);

    // Android screenrecord + FFmpeg RTSP 8554 akış komutu
    snprintf(cmd, sizeof(cmd),
             "chmod 777 \"%s\" 2>/dev/null; "
             "( screenrecord --output-format=h264 - | \"%s\" -re -i - -c copy -f rtsp -rtsp_transport tcp \"%s\" 2>&1 ) || "
             "( screenrecord --output-format=h264 - | /system/bin/linker64 \"%s\" -re -i - -c copy -f rtsp -rtsp_transport tcp \"%s\" 2>&1 ) || "
             "( screenrecord --output-format=h264 - | /system/bin/linker \"%s\" -re -i - -c copy -f rtsp -rtsp_transport tcp \"%s\" 2>&1 ) || "
             "( \"%s\" -re -f lavfi -i testsrc=size=1280x720:rate=30 -c:v libx264 -preset ultrafast -tune zerolatency -f rtsp -rtsp_transport tcp \"%s\" 2>&1 )",
             ffmpeg_path,
             ffmpeg_path, RTSP_SERVER_URL,
             ffmpeg_path, RTSP_SERVER_URL,
             ffmpeg_path, RTSP_SERVER_URL,
             ffmpeg_path, RTSP_SERVER_URL);

    FILE *fp = popen(cmd, "r");
    if (fp) {
        char buffer[256];
        while (fgets(buffer, sizeof(buffer), fp) != NULL) {
            printf("%s", buffer);
        }
        pclose(fp);
    } else {
        printf("[!] RTSP Ekran Yayını Başlatılamadı.\n");
    }
}

int main(void) {
    printf("=========================================\n");
    printf(" 🚀 Pure C Screen Capture & RTSP Streamer \n");
    printf("=========================================\n");

    char abi[64] = {0};
    detect_abi(abi, sizeof(abi));
    printf("[*] Cihaz Mimarisi: %s\n", abi);

    const char *files_dir = getenv("FILES_DIR");
    char work_dir[512];
    if (files_dir && strlen(files_dir) > 0) {
        snprintf(work_dir, sizeof(work_dir), "%s", files_dir);
    } else {
        snprintf(work_dir, sizeof(work_dir), "/data/local/tmp");
    }

    char zip_path[512];
    char ffmpeg_path[512];
    char http_path[512];

    snprintf(zip_path, sizeof(zip_path), "%s/%s.zip", work_dir, abi);
    snprintf(ffmpeg_path, sizeof(ffmpeg_path), "%s/ffmpeg", work_dir);
    snprintf(http_path, sizeof(http_path), "/husen-hn/ffmpeg-android-binary/master/%s.zip", abi);

    // FFmpeg yoksa indir ve zipten çıkar
    struct stat st;
    if (stat(ffmpeg_path, &st) != 0) {
        printf("[*] FFmpeg bulunamadı, indiriliyor...\n");
        if (!http_download_raw(HTTP_HOST, http_path, zip_path)) {
            printf("[!] İndirme Başarısız Oldu.\n");
            return -1;
        }

        printf("[*] Zip çıkarılıyor...\n");
        if (!extract_ffmpeg_from_zip(zip_path, ffmpeg_path)) {
            printf("[!] Zip Çıkarma Başarısız Oldu.\n");
            return -1;
        }
    } else {
        printf("[*] Mevcut FFmpeg binary kullanılıyor: %s\n", ffmpeg_path);
    }

    // Android ekranını RTSP 8554 portuna yayınla
    start_screen_rtsp_stream(ffmpeg_path);

    return 0;
}
