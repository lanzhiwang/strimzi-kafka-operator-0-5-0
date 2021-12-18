#!/bin/bash
set -x

# Write the config file
cat <<EOF
# the directory where the snapshot is stored.
dataDir=${ZOOKEEPER_DATA_DIR}
clientPort=$(expr 10 \* 2181 + $ZOOKEEPER_ID - 1)

# Provided configuration
${ZOOKEEPER_CONFIGURATION}
# Zookeeper nodes configuration
EOF

NODE=1
FOLLOWER_PORT=$(expr 10 \* 2888)
# FOLLOWER_PORT=28880
ELECTION_PORT=$(expr 10 \* 3888)
# ELECTION_PORT=38880

while [ $NODE -le $ZOOKEEPER_NODE_COUNT ]; do
  echo "server.${NODE}=127.0.0.1:$(expr $FOLLOWER_PORT + $NODE - 1):$(expr $ELECTION_PORT + $NODE - 1)"
  let NODE=NODE+1
done

# dataDir=/var/lib/zookeeper/data
# clientPort=21810
# ${ZOOKEEPER_CONFIGURATION}
# server.1=127.0.0.1:28880:38880
# server.1=127.0.0.1:28881:38881
