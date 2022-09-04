# Orkes Conductor
Orkes Conductor is a fully compatible version of Netflix Conductor with Orkes certified stack.

[![Github release](https://img.shields.io/github/v/release/Netflix/conductor.svg)](https://GitHub.com/Netflix/conductor/releases)
[![CI](https://github.com/orkes-io/orkes-conductor-community/actions/workflows/ci.yaml/badge.svg)](https://github.com/orkes-io/orkes-conductor-community/actions/workflows/ci.yml)
[![CI](https://img.shields.io/badge/license-orkes%20community%20license-green)](https://github.com/orkes-io/licenses/blob/main/community/LICENSE.txt)


<pre>
  ______   .______       __  ___  _______     _______.
 /  __  \  |   _  \     |  |/  / |   ____|   /       |
|  |  |  | |  |_)  |    |  '  /  |  |__     |   (----`
|  |  |  | |      /     |    <   |   __|     \   \    
|  `--'  | |  |\  \----.|  .  \  |  |____.----)   |   
 \______/  | _| `._____||__|\__\ |_______|_______/    
                                                      
  ______   ______   .__   __.  _______   __    __    ______ .___________.  ______   .______      
 /      | /  __  \  |  \ |  | |       \ |  |  |  |  /      ||           | /  __  \  |   _  \     
|  ,----'|  |  |  | |   \|  | |  .--.  ||  |  |  | |  ,----'`---|  |----`|  |  |  | |  |_)  |    
|  |     |  |  |  | |  . `  | |  |  |  ||  |  |  | |  |         |  |     |  |  |  | |      /     
|  `----.|  `--'  | |  |\   | |  '--'  ||  `--'  | |  `----.    |  |     |  `--'  | |  |\  \----.
 \______| \______/  |__| \__| |_______/  \______/   \______|    |__|      \______/  | _| `._____| 
</pre>

## Stack
1. **Redis** as a the primary store for running workflows
2. **Postgres** for storing completed workflows and indexing enabling full text search
3. **Orkes-Queues** Redis based queues that improve upon dyno-queues and providers higher performance and are built from ground up to support Redis standalone and cluster mode

## Running using Docker
Each release is published as a docker image.

### Fully self-contained standalone server with all the dependencies
Container image useful for local development and testing.  **Note:** self-contained docker image shouldn't be used in production environment.

#### Simple self-contained script to launch docker image
```shell
curl https://github.com/orkes-io/orkes-conductor-community/blob/scripts/run.sh | sh
```
#### Using `docker run` manually (provides more control)
```shell

# Create volumes for persistent stores
# Used to create a persistent volume that will preserve the 
docker volume create postgres
docker volume create redis

docker run --init -p 8080:8080 -p 1234:5000 --mount source=redis,target=/redis \
--mount source=postgres,target=/pgdata orkesio/orkes-conductor-community:latest
```
Navigate to http://localhost:1234 once the container starts to launch UI

#### Server + UI Docker
```shell
docker pull orkes-io/orkes-conductor-server:latest
```
>**Note** To use specific version of Conductor, replace `latest` with the release version
> e.g. 
> 
> ```docker pull orkes-io/orkes-conductor-server:3.11.0```


### License
Orkes Conductor is fully open source and is available under Orkes Community License

**TL;DR**
Orkes Community License allows anyone to download, redistribute and use Orkes Conductor version in production
