#!/bin/bash

for var in $@
do
    case $var in
        -userId=*)
            userId=`echo $var | cut -f2 -d\=` 
            ;;
        -password=*)
            password=`echo $var | cut -f2 -d\=` 
            ;;
        -isAdmin=*)
            isAdmin=`echo $var | cut -f2 -d\=` 
            ;;
        -jbossDomainBaseDir=*)
            jbossDomainBaseDir=`echo $var | cut -f2 -d\=` 
            ;;
        -domainConfig=*)
            domainConfig=`echo $var | cut -f2 -d\=` 
            ;;
        -cliPort=*)
            cliPort=`echo $var | cut -f2 -d\=` 
            ;;
        -node=*)
            node=`echo $var | cut -f2 -d\=` 
            ;;
        -jbossHome=*)
            jbossHome=`echo $var | cut -f2 -d\=` 
            ;;
        -jbossDomainBaseDir=*)
            jbossDomainBaseDir=`echo $var | cut -f2 -d\=` 
            ;;
        -jbossCliXmx=*)
            jbossCliXmx=`echo $var | cut -f2 -d\=` 
            ;;
        -cliFile=*)
            cliFile=`echo $var | cut -f2 -d\=` 
            ;;
        -hostName=*)
            hostName=`echo $var | cut -f2 -d\=` 
            ;;
        -cliCommand=*)
            cliCommand=`echo $var | cut -f2 -d\=` 
            ;;
        -sleepSec=*)
            sleepSec=`echo $var | cut -f2 -d\=`
            ;;
        -serverIpAddr=*)
            serverIpAddr=`echo $var | cut -f2 -d\=` 
            ;;
        -port=*)
            port=`echo $var | cut -f2 -d\=` 
            ;;
        -orgName=*)
            orgName=`echo $var | cut -f2 -d\=` 
            ;;
        -webContext=*)
            webContext=`echo $var | cut -f2 -d\=` 
            ;;
        -taskId=*)
            taskId=`echo $var | cut -f2 -d\=` 
            ;;
        -deployId=*)
            deployId=`echo $var | cut -f2 -d\=` 
            ;;
    esac
done

checkHostName() {
    if [ "x$hostName" = "x" ]; then
        hostName=$HOSTNAME
    fi
    if ping -c 1 $hostName > /dev/null 2>&1
    then
        echo "we are online!"
    else
        echo -en "\n unable to ping $hostName.  check your network settings"
        exit 1
    fi
}

start() {
    checkHostName

    if [ "x$jbossDomainBaseDir" = "x" ]; then
        jbossDomainBaseDir=domain
    fi
    if [ "x$domainConfig" = "x" ]; then
        domainConfig=domain.xml
    fi
    if [ "x$hostName" = "x" ]; then
        hostName=$HOSTNAME
    fi
    if [ "x$jbossHome" = "x" ]; then
        jbossHome=$JBOSS_HOME
    fi
    echo -en $"Starting jboss daemon at $jbossHome w/ following command line:\n  nohup ./bin/domain.sh -b=$hostName -bmanagement=$hostName -Djboss.domain.base.dir=$jbossDomainBaseDir -Ddomain-config=$domainConfig & \n"
    sleep 1 
    cd $jbossHome
    chmod 755 $jbossHome/bin/*.sh
    rm nohup.out
    nohup ./bin/domain.sh -b=$hostName -bmanagement=$hostName -Djboss.domain.base.dir=$jbossDomainBaseDir -Ddomain-config=$domainConfig &
    if [ "x$sleepSec" !=  "x" ]; then
        sleep $sleepSec
    else
        sleep 3
    fi

}

stop() {
    if [ "x$cliPort" = "x" ]; then
        cliPort=9999
    fi
    if [ "x$node" = "x" ]; then
        node=master
    fi

    echo -en $"stopping following jboss node: $node\t: at $hostName:$cliPort\t : jbossHome = $jbossHome\n"
    cd $jbossHome
    chmod 755 $jbossHome/bin/*.sh
    ./bin/jboss-cli.sh --connect --controller=$hostName:$cliPort --command=/host=$node:shutdown
    echo
    sleep 3
}

restart() {
    stop
    start
}

executeAddUser() {
    echo -en "executeAddUser() : isAdmin = $isAdmin : userId = $userId \n"
    cd $jbossHome
        ./bin/add-user.sh $userId $password  --silent=false
    #if [$isAdmin -eq "true"]; then
    #    ./bin/add-user.sh $userId $password 
    #else
    #    ./bin/add-user.sh $userId $password -a
    #fi
}

executeCli() {
    echo -en "executeCli() cliCommand = $cliCommand\n"
    chmod 755 $jbossHome/bin/*.sh

    export JAVA_OPTS=-Xmx$jbossCliXmx

    if [ "x$cliCommand" !=  "x" ]; then
        $jbossHome/bin/jboss-cli.sh --connect --controller=$hostName:$cliPort --command=$cliCommand
    else
        $jbossHome/bin/jboss-cli.sh --connect --controller=$hostName:$cliPort -c --file=$cliFile
    fi
}

# ./bin/local.jboss.domain.sh refreshSlaveHosts -serverIpAddr=eap6cluster1 -orgName=gpe
refreshSlaveHosts() {
    echo -en "refreshSlaveHosts() serverIpAddr = $serverIpAddr : orgName=$orgName\n"
    #  0)  verify network connectivity to remote host
    port=22;
    checkRemotePort;
    if [ $socketIsOpen -ne 0 ]; then
        exit 1
    fi

    # 1)  kill any existing java processes on remote node  
    ssh jboss@$serverIpAddr 'for jProc in `ps -C java -o pid=`;
        do
            echo -en "about to kill java process id = $jProc\n"
            kill -9 $jProc
        done
    '
    # 2) scp .bashrc with module path env variable 
    scp conf/shell/slavebashrc jboss@$serverIpAddr:/home/jboss/.bashrc

    # 3)  blow away existing eap
    ssh jboss@$serverIpAddr 'mkdir -p ~/jbossProjects/downloads; rm -rf $JBOSS_HOME'

    # 4)  scp and  unzip jboss in remote host
    remoteZip=$(ssh jboss@$serverIpAddr "ls ~/jbossProjects/downloads/jboss-eap-6.0.1.zip")
    if [ "x$remoteZip" == "x" ]; then
        scp target/lib/jboss-eap-6.0.1.zip jboss@$serverIpAddr:/home/jboss/jbossProjects/downloads
    fi

    # 5)  clone domain root and rename to domain-$orgName
    ssh jboss@$serverIpAddr "unzip ~/jbossProjects/downloads/jboss-eap-6.0.1.zip -d /opt; cp -r /opt/jboss-eap-6.0/domain /opt/jboss-eap-6.0/domain-$orgName"

    # 6) rsync modules, slave host.xml, application-roles.properties and application-users.properties
    rsync -avz target/jboss/* jboss@$serverIpAddr:/opt/jboss-eap-6.0

        #                            - attempt to use generic userId for communication back to parent process controller (rather than userId equivalent to hostname)
        #                            - may need to generate management user for each host in master node (depending on step #2)

    # 7)  start remote host
    ssh jboss@$serverIpAddr "cd /opt/jboss-eap-6.0; nohup ./bin/domain.sh -b=$serverIpAddr -Djboss.domain.base.dir=domain-$orgName -Djboss.domain.master.address=$HOSTNAME -Djboss.domain.master.port=9999 > eap.log 2>&1 &"
}

# Test remote host:port availability (TCP-only as UDP does not reply)
function checkRemotePort() {
    echo "checkRemotePort"
    (echo >/dev/tcp/$serverIpAddr/$port) &>/dev/null
    if [ $? -eq 0 ]; then
        echo -en "\n$serverIpAddr:$port is open."
        socketIsOpen=0
    else
        echo -en "\n$serverIpAddr:$port is closed."
        socketIsOpen=1
    fi
}


function killJbossProcesses() {
    sleep 2;
    for jProc in `ps -C java -o pid=`;
    do
        pInfo=$(ps -p $jProc -f)
        if [[ $pInfo =~ .*jboss.modules.system.pkgs.* ]];
        then
            if [[ $pInfo =~ .*org.jboss.as.cli.* ]];
            then
                echo -en "\nkillJavaProcesses() will not kill jboss cli = $jProc\n"
            else
                echo -en "killJavaProcesses() about to kill jboss process id = $jProc\n"
                kill -9 $jProc
            fi
        else
            echo -en "\nkillJavaProcesses() will not kill java process = $jProc\n"
        fi
    done
}


case "$1" in
    start|stop|restart|executeCli|refreshSlaveHosts|killJbossProcesses)
        $1
        ;;
    *)
    echo 1>&2 $"Usage: $0 {start|stop|restart|executeAddUser|executeCli|refreshSlaveHosts|killJbossProcesses}"
    exit 1
esac
