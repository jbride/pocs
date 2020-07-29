#!/bin/bash

# Assumptions:
## Existing disk mounts :   /u0A, /u0B and /u0C

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
echo -en "\nSCRIPT_DIR = $SCRIPT_DIR\n\n"

MAX_PV_ID=20
MOUNT_TARGET_DIR_ROOT=/srv/nfs4

function create() {
    echo "$MOUNT_TARGET_DIR_ROOT               192.168.122.0/24(rw,sync,no_subtree_check,crossmnt,fsid=0)" > /etc/exports

    for ((MINOR_DISK=10;MINOR_DISK<=$MAX_PV_ID;MINOR_DISK++)); do 

      # Create NFS mounts
      DIR_NAME=u0$MAJOR_DISK$MINOR_DISK
      mkdir -p /u0$MAJOR_DISK/$DIR_NAME
      mkdir -p $MOUNT_TARGET_DIR_ROOT/$DIR_NAME
      mount --bind /u0$MAJOR_DISK/$DIR_NAME $MOUNT_TARGET_DIR_ROOT/$DIR_NAME
      echo "$MOUNT_TARGET_DIR_ROOT/$DIR_NAME/        192.168.122.0/24(rw,sync,no_subtree_check)" >> /etc/exports
      exportfs -ra

      # Create corresponding PV
      lowercase="${MAJOR_DISK,,}"
      name=pvu0$lowercase$MINOR_DISK
      echo -en "\nname = $name\n"
      path=$DIR_NAME

      # https://docs.openshift.com/container-platform/4.4/storage/persistent_storage/persistent-storage-nfs.html
      cat $SCRIPT_DIR/pv.yaml | sed "s/{name}/$name/g" | sed "s/{path}/$path/g" | oc create -f -

    done
    chmod -R 777 /u0$MAJOR_DISK/
}

function cleanAll() {
    oc delete pv --all
    echo "$MOUNT_TARGET_DIR_ROOT               192.168.122.0/24(rw,sync,no_subtree_check,crossmnt,fsid=0)" > /etc/exports
    exportfs -ra

    for ((MINOR_DISK=10;MINOR_DISK<=$MAX_PV_ID;MINOR_DISK++)); do 
        DIR_NAME=u0$MAJOR_DISK$MINOR_DISK
        echo -en "\nunmounting: $MOUNT_TARGET_DIR_ROOT/$DIR_NAME\n"
        umount $MOUNT_TARGET_DIR_ROOT/$DIR_NAME
        sleep 5
        rm -rf /u0$MAJOR_DISK/$DIR_NAME
        rm -rf $MOUNT_TARGET_DIR_ROOT/$DIR_NAME
    done
}

function refresh() {
    for ((MINOR_DISK=10;MINOR_DISK<=$MAX_PV_ID;MINOR_DISK++)); do 
      # Create corresponding PV
      lowercase="${MAJOR_DISK,,}"
      name=pvu0$lowercase$MINOR_DISK
      path=$DIR_NAME
      status=$(oc get pv $name -o template --template {{.status.phase}})
      if [ $? -eq 0 ];then
          echo -en "\nrefresh: $name $status\n"
          if [ "Released" = $status ]; then
            echo -en "\nAbout to refresh: $name\n"
            oc delete pv $name
            rm -rf /u0$MAJOR_DISK/$DIR_NAME/*
            cat $SCRIPT_DIR/pv.yaml | sed "s/{name}/$name/g" | sed "s/{path}/$path/g" | oc create -f -
          fi
      fi
    done

}

for MAJOR_DISK in A B C; do
    case "$1" in
        create|refresh|clean)
            $1
            ;;
        *)
        echo 1>&2 $"Usage: $0 {create|refresh|clean}"
        exit 1
    esac

done

