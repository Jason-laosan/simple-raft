#!/bin/bash
echo "Starting Raft Node 3 (Port 8003)..."
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8003 localhost:8001,localhost:8002
