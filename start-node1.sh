#!/bin/bash
echo "Starting Raft Node 1 (Port 8001)..."
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8001 localhost:8002,localhost:8003
