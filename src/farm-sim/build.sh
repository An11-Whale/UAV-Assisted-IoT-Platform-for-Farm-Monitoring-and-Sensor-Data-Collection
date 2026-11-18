#!/bin/sh
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name '*.java') && echo "Xong. Chay: java -cp out farm.Main"
