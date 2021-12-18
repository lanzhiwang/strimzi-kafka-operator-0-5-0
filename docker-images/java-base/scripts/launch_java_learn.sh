#!/bin/sh

# JAVA_OPTS="-Dkey1=value1 -Dkey2=value2" /bin/launch_java.sh test.jar 1 2 3

set -x
JAR=$1
# JAR=test.jar
shift

. /bin/dynamic_resources.sh

MAX_HEAP=`get_heap_size`
# MAX_HEAP=

if [ -n "$MAX_HEAP" ]; then
  JAVA_OPTS="-Xms${MAX_HEAP}m -Xmx${MAX_HEAP}m $JAVA_OPTS"
fi

export MALLOC_ARENA_MAX=2

# Make sure that we use /dev/urandom
JAVA_OPTS="${JAVA_OPTS} -Dvertx.cacheDirBase=/tmp -Djava.security.egd=file:/dev/./urandom"
# JAVA_OPTS='-Dkey1=value1 -Dkey2=value2 -Dvertx.cacheDirBase=/tmp -Djava.security.egd=file:/dev/./urandom'

# Enable GC logging for memory tracking
JAVA_OPTS="${JAVA_OPTS} -XX:NativeMemoryTracking=summary -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps"
# JAVA_OPTS='-Dkey1=value1 -Dkey2=value2 -Dvertx.cacheDirBase=/tmp -Djava.security.egd=file:/dev/./urandom -XX:NativeMemoryTracking=summary -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps'

exec java $JAVA_OPTS -jar $JAR $JAVA_OPTS $@

# exec java
# -Dkey1=value1
# -Dkey2=value2
# -Dvertx.cacheDirBase=/tmp
# -Djava.security.egd=file:/dev/./urandom
# -XX:NativeMemoryTracking=summary
# -verbose:gc
# -XX:+PrintGCDetails
# -XX:+PrintGCDateStamps
# -jar test.jar
# -Dkey1=value1
# -Dkey2=value2
# -Dvertx.cacheDirBase=/tmp
# -Djava.security.egd=file:/dev/./urandom
# -XX:NativeMemoryTracking=summary
# -verbose:gc
# -XX:+PrintGCDetails
# -XX:+PrintGCDateStamps
# 1 2 3
