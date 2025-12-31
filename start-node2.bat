@echo off
echo Starting Raft Node 2 (Port 8002)...
java -jar target/simple-raft-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 8002 localhost:8001,localhost:8003
pause
