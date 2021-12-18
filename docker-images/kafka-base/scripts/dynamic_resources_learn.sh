#!/bin/sh

set -x

function get_heap_size {
  FRACTION=$1
  # FRACTION=0.5

  MAX=$2
  # MAX=0.5

  # Get the max heap used by a jvm which used all the ram available to the container
  MAX_POSSIBLE_HEAP=$(java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -XshowSettings:vm -version \
    |& awk '/Max\. Heap Size \(Estimated\): [0-9KMG]+/{ print $5}' \
    | gawk -f to_bytes.gawk)

  ACTUAL_MAX_HEAP=$(echo "${MAX_POSSIBLE_HEAP} ${FRACTION}" | awk '{ printf "%d", $1 * $2 }')

  if [ ${MAX} ]; then
    MAX=$(echo ${MAX} | gawk -f to_bytes.gawk)
    if [ ${MAX} -lt ${ACTUAL_MAX_HEAP} ]; then
      ACTUAL_MAX_HEAP=$MAX
    fi
  fi
  echo $ACTUAL_MAX_HEAP
}


# get_heap_size 0.5 0.5

# $ java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -XshowSettings:vm -version | awk '/Max\. Heap Size \(Estimated\): [0-9KMG]+/{ print $5}'
# VM settings:
#     Max. Heap Size (Estimated): 20.79G
#     Ergonomics Machine Class: server
#     Using VM: OpenJDK 64-Bit Server VM

# openjdk version "1.8.0_302"
# OpenJDK Runtime Environment (build 1.8.0_302-b08)
# OpenJDK 64-Bit Server VM (build 25.302-b08, mixed mode)
# $
# $ java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -XshowSettings:vm -version | awk '/Max\. Heap Size \(Estimated\): [0-9KMG]+/{ print $5}' | gawk -f to_bytes.gawk
# VM settings:
#     Max. Heap Size (Estimated): 20.79G
#     Ergonomics Machine Class: server
#     Using VM: OpenJDK 64-Bit Server VM

# openjdk version "1.8.0_302"
# OpenJDK Runtime Environment (build 1.8.0_302-b08)
# OpenJDK 64-Bit Server VM (build 25.302-b08, mixed mode)
# $
