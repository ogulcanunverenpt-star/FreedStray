'use strict';

const cluster = require('node:cluster');
const os = require('node:os');
const process = require('node:process');

if (cluster.isPrimary) {
  const numCPUs = os.availableParallelism ? os.availableParallelism() : os.cpus().length;
  console.log(`[Cluster Primary] Master PID ${process.pid} is running. Forking ${numCPUs} CPU workers for Railway...`);

  for (let i = 0; i < numCPUs; i++) {
    cluster.fork();
  }

  cluster.on('exit', (worker, code, signal) => {
    console.log(`[Cluster Primary] Worker ${worker.process.pid} died (${code || signal}). Restarting replacement worker...`);
    cluster.fork();
  });
} else {
  // Worker process loads server.js logic
  require('./server.js');
}
