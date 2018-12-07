rabbitmq-build-trigger: RabbitMQ Build Trigger Plugin for Jenkins
=======================================================

* Author: rinrinne a.k.a. rin_ne
* Repository: http://github.com/jenkinsci/rabbitmq-build-trigger-plugin
* Plugin Information: https://wiki.jenkins-ci.org/display/JENKINS/RabbitMQ+Build+Trigger+Plugin

Synopsis
------------------------

rabbitmq-build-trigger is a Jenkins plugin to trigger build using application message for remote build in specific queue on RabbitMQ.

Usage
------------------------

You need to install [RabbitMQ Consumer Plugin][rabbitmq-consumer] and configure it before using this plugin.

If you install this, *RabbitMQ Build Trigger* setting is added into your job project's build trigger section. please enable it then set your token. So build would be triggered if appropriate application message arrives.

Also adds *Publish build result to RabbitMQ* to Post-build Actions in your job's configuration. If you set this action, build result message is published to your specified exchange or queue in RabbitMQ. Published message is the below:

Properties:
```
content_type: application/json
app_id: remote-build
routingkey: ROUTINGKEY or org.jenkinsci.plugins.rabbitmqbuildtrigger
```

Message header:
```
jenkins-url: JENKINS_ROOT_URL
```

Meesage body:
```json
{
    "project": "PROJECTNAME",
    "number": "BUILDNUMBER",
    "status": "SUCCESS|FAILURE|UNSTABLE.."
}
```

Application Message Format
------------------------

```json
{
    "project": "RPROJECTNAME",
    "token": "TOKEN",
    "parameter": [
        {
            "name": "PARAMETERNAME",
            "value": "VALUE"
        },
        {
            "name": "PARAMETERNAME2",
            "value": "VALUE2"
        }
    ]
}
```

name in each parameters is compared with existing parameter name by case-insensitive.

A message must have two properties.

```
content_type: application/json
app_id: remote-build
```

Pipeline support
------------------------

This plugin can trigger pipeline jobs. In addition, this provides followings:

* A trigger *rmqRemoteBuild* to *triggers* directive in pipeline script.
* A notifier *rmqRemotePublish* to *step* or *post* directive in pipeline script.

This can be used in *Jenkinsfile* e.g:

```
pipeline {
    triggers {
        rmqRemoteBuild(remoteBuildToken: '<TOKEN>')
    }
    post {
      always {
        rmqRemotePublish(brokerName: '<EXCHANGE>', routingKey: '<ROUTINGKEY>')
      }
    }
}
```

Material
------------------------

* [RabbitMQ Consumer Plugin][rabbitmq-consumer]

[rabbitmq-consumer]: http://wiki.jenkins-ci.org/display/JENKINS/RabbitMQ+Consumer+Plugin

License
------------------------

MIT License

Copyright
------------------------

Copyright (c) 2013-2018 rinrinne a.k.a. rin_ne
