#!/bin/bash

FILE_LIST=$(ls src/test/kotlin/files/small_files | sed 's/[12].txt//g' | sort | uniq)
PREFIX=src/test/kotlin/files/small_files/

for GROUP in $FILE_LIST
do
  echo "File ${GROUP}1.txt:"
  cat -n ${PREFIX}${GROUP}1.txt

  echo File ${GROUP}2.txt:
  cat -n ${PREFIX}${GROUP}2.txt

  echo Normal diff:
  java -jar diff.jar ${PREFIX}${GROUP}1.txt ${PREFIX}${GROUP}2.txt

  echo Normal diff backwards:
  java -jar diff.jar ${PREFIX}${GROUP}2.txt ${PREFIX}${GROUP}1.txt

  echo Unified \(3 context lines\) diff:
  java -jar diff.jar -u ${PREFIX}${GROUP}1.txt ${PREFIX}${GROUP}2.txt

  echo Unified \(3 context lines\) diff backwards:
  java -jar diff.jar -u ${PREFIX}${GROUP}2.txt ${PREFIX}${GROUP}1.txt
done
