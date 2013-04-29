Depository
==========

Vert.x application to store static files and serve them after GET requests using basic http auth.


Prerequisites
-------------

- Java 7

Tested using:

- Vert.x 1.3.1.final


One minute test
---------------

Get sources, then:

    cd depository
    vertx run server.groovy -conf app.json

Depository will run at http://localhost:8080

Verify status:

    curl http://localhost:8080/status/wazzup

The expected response is:

    wazzup:OK

Download file:

    curl -u APIBOT345:apisecretrobot http://localhost:8080/dl/test.txt


Configuration
-------------

Edit the sample file `data/users.json` adding the authorized users and the api keys.

Edit the sample configuration file `app.json`; in this file you can configure:

- if app is running on http or https, on which ports, etc

- paths to keystore, json file containing users, etc

- if downloads are enabled

- if authentication is needed

- if accept only requests via https


In the `samples` directory you can find examples for configuration files.


Run
---

Put the files you want to serve in `dl/` directory and run the app:

    vertx run server.groovy -conf app.json


Getting files
-------------

Add user, api key and api secret to `users.json`.

Get request with curl:

    curl -u "${API_KEY}:${API_SECRET}" http://localhost:8080/dl/my/awesome/file.txt

If Depository is running over https (with self-certified keys) add options:

    curl -u "${API_KEY}:${API_SECRET}" --insecure --sslv3 https://localhost:4443/dl/my/awesome/file.txt


Https
-----  

Configure `app.json`: to run on https you need only to set `https.enabled` to `true`; this configuration is enough to run a development instance.

In production, probably, you want customize something...

To generate key:

    keytool -genkey -keyalg RSA -alias selfsigned -keystore data/keystore.jks \
    -storepass agoodpassword -validity 360 -keysize 2048

Your application could run in an environment accepting http *and* https requests; you can configure Depository to accept only https for downloads
disabling https (so app will start using http) and using `downloads.secure_only` in `app.json`.

Depository will look for a request header named `X-Forwarded-Proto` and request will be considered secure if this header is set to `https`.


Heroku deployment in 5 minutes
------------------------------

- create Heroku app using [enr/vertx buildpack](https://github.com/enr/heroku-buildpack-vertx):

    `heroku create --stack cedar --buildpack https://github.com/enr/heroku-buildpack-vertx.git`

    This is a fork of a common Vert.x buildpack, the only difference is the option `-conf` to the `vertx run` command.

- enable http and disable https in `app.json`

- set `downloads.secure_only` to `true`

- push to Heroku

- get files from `https://$herokuapp/$downloadspath`

In the `samples` directory you can find an example of configuration file optimized for Heroku deployment.


Run tests
---------

Start Vertx server.

If you are testing a local development version remember to run it using a localhost optimized configuration ie `cp samples/localhost-app.json app.json`

When server is running:

    groovy util/runtests.groovy [-c conf] [-b baseurl]

Without any other param, test script will take configuration data from `app.json` file.

You can specify the configuration file or the url to test.

**Https**

To test https you need some manual steps.

Run server.

When server is running, in another shell install certificate (you can ignore SSLHandshakeException):

    javac util/InstallCert.java
	java util/InstallCert localhost:4443

This will prompt for add certificate to keystore; accept

	sudo cp jssecacerts ${JAVA_HOME}/jre/lib/security/

Then run tests using the https url

	groovy util/runtests.groovy https://localhost:4443


Licensing
---------

Copyright (C) 2013 - enr

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.

You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.

