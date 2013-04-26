#!/usr/bin/env groovy

@Grab(group='com.github.groovy-wslite', module='groovy-wslite', version='0.7.2')
import wslite.http.auth.*
import wslite.rest.*

import groovy.json.*

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
new javax.net.ssl.HostnameVerifier(){
    public boolean verify(String hostname,
            javax.net.ssl.SSLSession sslSession) {
        if (hostname.equals("localhost")) {
            return true;
        }
        return false;
    }
});

def parseargs(args) {
    def cli = new CliBuilder(usage: 'runtests.groovy --baseurl [URL] --conf [CONFIGFILE]')
    // Create the list of options.
    cli.with {
        h longOpt: 'help', 'Show usage information'
        b longOpt: 'baseurl', args: 1, argName: 'base url', 'Base url for requests'
        c longOpt: 'conf', args: 1, argName: 'conf file', 'Configuration file used from the running app'
    }
    
    def options = cli.parse(args)
    if (!options) {
        return
    }
    // Show usage text when -h or --help option is used.
    if (options.h) {
        cli.usage()
        return
    }

    def baseUrl = ''
    def configFile = 'app.json'
    if (options.b) {
         baseUrl = options.b
    }
    if (options.c) {
         configFile = options.c
    }
    println "url=${baseUrl}"
    println "conf=${configFile}"
    return [url:baseUrl, conf:configFile]
}

tools = new util.tools() //evaluate(new File("lib/tools.groovy"))

def opts = parseargs args
def serverUrl = opts.url

if (!serverUrl) {
    //tools.error "Base url missing"
    println "I'll test localhost, using protocol and port from configuration."
} else {
    if (!tools.isURLValid(serverUrl)) {
        tools.error "Error: '${serverUrl}' does not appear as a valid Depository url"
        System.exit 1
    }
    println "Testing Depository ${serverUrl}"
}

def configFile = opts.conf

def app = tools.loadConfiguration(configFile)   // arg[1] ?

tools.printConfiguration(app)

// --- users data
def usersDataFile = app.data.users
// CHANGE ME!
def defaultUsersData = [ 
  users: [
    admin : [ password: 'adminadmin'],
    robot : [ password: 'robotrobot']
  ],
  api: [
    APIADM123: [user:'admin', secret:'apisecretadmin'],
    APIBOT345: [user:'robot', secret:'apisecretrobot']
  ]
]

def auth = tools.loadConfiguration(usersDataFile) ?: defaultUsersData
def API_KEY = 'APIBOT345'
def API_SECRET = 'apisecretrobot'


def protocol = app.https.enabled ? 'https' : 'http'
def port = app.https.enabled ? app.https.port : app.http.port
def baseUrl = serverUrl ?: "${protocol}://localhost:${port}"
println "Using base url: ${baseUrl}"

def verifier = System.currentTimeMillis()

def client = new RESTClient(baseUrl)
client.authorization = new HTTPBasicAuthorization(API_KEY, API_SECRET)
def response
try {
    response = client.get(path:"/status/${verifier}")
    assert 200 == response.statusCode
    assert "${verifier}:OK" == tools.tostr(response).trim()
    println "Status OK"
} catch (Exception exception) {
    if (exception.cause instanceof java.net.ConnectException) {
        tools.error "Connection error. Maybe Vertx server is not running?"
    } else {
        tools.error "Error:\n${exception.message}"
    }
    System.exit 1
}

def downloadsBaseUrl = baseUrl
if (app.downloads.secure_only) {
    def buu = new URL(downloadsBaseUrl)
    downloadsBaseUrl = downloadsBaseUrl.replace("${baseUrl.protocol}://", "https://")
}

//def downloadsProtocol = app.downloads.secure_only ? 'https' : protocol
//def downloadsBaseUrl = serverUrl ?: "${downloadsProtocol}://localhost:${port}"
def downloadsClient = new RESTClient(downloadsBaseUrl)
downloadsClient.authorization = new HTTPBasicAuthorization(API_KEY, API_SECRET)
Path startingDir = Paths.get(app.downloads.path);
Files.walkFileTree(startingDir, new VerifierVisitor(downloadsClient, tools));

class VerifierVisitor extends SimpleFileVisitor<Path> {
    final RESTClient client
    def tools
    public VerifierVisitor(RESTClient client, tools) {
        this.client = client
        this.tools = tools
    }
    @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
        String filePath = path.normalize().toString().replace(File.separator, "/")
        String localFileContents = path.toFile().text
        verify("/${filePath}", localFileContents)
        return FileVisitResult.CONTINUE;
    }
    private void verify(path, responseText) {
        def response = client.get(path:path)
        assert 200 == response.statusCode
        assert responseText == tools.tostr(response)
        println "${path} OK"
    }
}
