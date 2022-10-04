
# Contributing
Thanks for your interest in contributing to Orkes. This guide helps to find the most efficient way to contribute, ask questions, and report issues.

## I want to contribute

We welcome Pull Requests and already had many outstanding community contributions! Creating and reviewing Pull Requests take considerable time. This section helps you to set up a smooth Pull Request experience.

We're currently accepting contributions for our [Community repository](https://github.com/orkes-io/orkes-conductor-community)
Please create pull requests for your contributions against Orkes Conductor community repo only.

Also, please consider that not every feature is a good fit for Orkes. A few things to consider for your contributions are:

* Is it increasing complexity for the user, or might it be confusing?
* Does it, in any way, break backward compatibility (this is seldom acceptable)
* Does it require new dependencies (this is rarely acceptable for core modules)
* Should the feature be opt-in or enabled by default. For integration with a new Queuing recipe or persistence module, a separate module which can be optionally enabled is the right choice.
* Should the feature be implemented in the main Orkes-Conductor repository, or would it be better to set up a separate repository? Especially for integration with other systems, a separate repository is often the right choice because the life-cycle of it will be different.
Of course, for more minor bug fixes and improvements, the process can be more light-weight.

We'll try to be responsive to Pull Requests. Do keep in mind that because of the inherently distributed nature of open source projects, responses to a PR might take some time because of time zones, weekends, and other things we may be working on.

Of course, for more minor bug fixes and improvements, the process can be more light-weight.

We'll try to be responsive to Pull Requests. Do keep in mind that because of the inherently distributed nature of open source projects, responses to a PR might take some time because of time zones, weekends, and other things we may be working on.


##I want to report an issue
-----

If you found a bug, it is much appreciated if you create an issue. Please include clear instructions on how to reproduce the issue, or even better, include a test case on a branch. Make sure to come up with a descriptive title for the issue because this helps while organizing issues.


##


# Configuration Properties for Orkes-Conductor

## Default configuration
The default configuration is set in the [application.properties](server/src/main/resources/application.properties) that is good enough for most use cases.

### Properties that can be overridden for production use case.
Please note, the server comes with sensible defaults that works for most part, 
```properties

##########################################
##### Redis database connectivity #####
##########################################
conductor.redis-lock.serverAddress=redis://HOSTNAME:PORT
# us-east-1c is used by backward compatibility. Change this to the existing value used 
conductor.redis.hosts=HOSTNAME:PORT:us-east-1c

##########################################
##### Postgres database connectivity #####
##########################################
spring.datasource.url=jdbc:postgresql://PG_HOST:PORT/db_name

#set the user and password accordingly
spring.datasource.username=
spring.datasource.password=


##########################################
##### Misc conductor server properties
##########################################

# Enable/Disable ownerEmail field in the metadata being required
# true | false
conductor.app.owner-email-mandatory=

####################################################################################
##### Advanced performance settings -- The value shown is the default value ########
####################################################################################
# No. of threads allocated to the sweeper.  Recommended to keep 2x-3x of the total CPU count on the host
conductor.app.sweeperThreadCount=10

# Batch size for the system task worker
conductor.app.systemTaskMaxPollCount=10
# No of threads configured for the system task worker.  Recommended to be same as systemTaskMaxPollCount
conductor.app.systemTaskWorkerThreadCount=10

# Keep metadata longer in the cache to avoid excessive lookups from Redis.  Recommended value 60
# Values are in second
conductor.redis.taskDefCacheRefreshInterval=1

# Postgres connection pool size -- typically no need to change, unless you are sure
# number.  default: 8
spring.datasource.hikari.maximum-pool-size=8

```
