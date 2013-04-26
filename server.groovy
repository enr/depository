
/*
Main script for FileRepository depository

vertx run server.groovy -conf app.json
*/

import org.vertx.groovy.core.http.RouteMatcher
import org.vertx.groovy.core.http.HttpServerRequest
//import org.vertx.java.core.json.JsonObject
import org.vertx.java.core.http.impl.ws.Base64

tools = new util.tools()

// --- base vars initialization
logger = container.logger

// --- constants
HTTP_DEFAULT_PORT = 8080
HTTPS_DEFAULT_PORT = 4443

// --- messages
AUTH_FAILED_MESSAGE = 'Authentication failed'
PAGE_NOT_FOUND_MESSAGE = 'Resource not found'
PROTO_ERROR_MESSAGE = 'Protocol error: only https requests accepted'

// --- configuration paths
def applicationConfigurationPath = 'app.json'

def environment = container.getEnv()
//logger.info "Container environment (${environment.getClass().getName()}):\n${environment}\n"

def app = container.config ?: tools.loadConfiguration(applicationConfigurationPath) // ?: [:]
if (!app) {
    logger.error "No application config... Exit"
    container.exit()
}

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

title "Authorized users"
printAuth auth

//println "\n\nconfiguration=${container.config}\n\n"

// --- router
def rm = new RouteMatcher()

rm.get('/status/:verifier') { req ->
    req.response.end "${req.params['verifier']}:OK\n"
}

// --- downloads
def downloadsPath = app.downloads.path
// Catch all in $downloadsPath
rm.getWithRegEx("/${downloadsPath}/.*") { req ->
    if (!app.downloads.enabled) {
        logger.info "Required disabled download ${req.uri}"
        resourceNotFound(req)
        //req.response.statusCode = 404
        //req.response.statusMessage = PAGE_NOT_FOUND_MESSAGE
        //req.response.end PAGE_NOT_FOUND_MESSAGE
        return
    }
    logger.info "Required download ${req.uri}"
    if ((app.downloads.secure_only == true) && (!isSecure(req, app))) {
        logger.info "Protocol error for download ${req.uri}"
        req.response.statusCode = 403
        req.response.statusMessage = PROTO_ERROR_MESSAGE
        req.response.end PROTO_ERROR_MESSAGE
        return
    }
    if ((app.auth.enabled == false) || (isAuthenticatedViaApi(req, auth.api))) {
        def filePath = req.path - '/'
        def file = new File(filePath)
        if (file.exists()) {
            req.response.sendFile filePath
        } else {
            logger.info "Required download not found ${filePath}"
            req.response.statusCode = 404
            req.response.statusMessage = PAGE_NOT_FOUND_MESSAGE
            req.response.end PAGE_NOT_FOUND_MESSAGE
        }
    } else {
        logger.info "Authentication failed for download ${req.uri}"
        req.response.statusCode = 403
        req.response.statusMessage = AUTH_FAILED_MESSAGE
        req.response.end AUTH_FAILED_MESSAGE
    }
}

// --- no match
rm.noMatch{ req ->
    logger.info "No match for ${req.uri}"
    req.response.statusCode = 404
    req.response.statusMessage = PAGE_NOT_FOUND_MESSAGE
    req.response.end PAGE_NOT_FOUND_MESSAGE
}

// --- server
def serverParams = [:]
def serverPort = HTTP_DEFAULT_PORT
if (System.getenv('PORT')) {
    serverPort = Integer.parseInt(System.getenv('PORT'))
} else {
    serverPort = app.http.port
    if ( app.https.enabled) {
        serverParams = [SSL: true, keyStorePath: app.https.keystore.path, keyStorePassword: app.https.keystore.password]
        serverPort = app.https.port ?: HTTPS_DEFAULT_PORT
    }
}

title "Server starting"
logger.info "Using port ${serverPort} and start params ${serverParams}"

def server = vertx.createHttpServer(serverParams)
server.requestHandler(rm.asClosure()).listen(serverPort)

// curl -u apikey:apisecret http://localhost:8080/dl/new/file.txt
def isAuthenticatedViaApi(HttpServerRequest request, config) {
    printRequestHeaders request
    def authorization = request.headers.authorization
    if (authorization) {
        def bytes = Base64.decode(authorization - 'Basic ')
        def apiKey
        def apiSecret
        try {
            def authData = new String(bytes)
            (apiKey, apiSecret) = authData.split(":")
        } catch (Throwable t) {
            logger.info "Error processing request authorization=${authorization}"
            return false
        }
        logger.info "Processing apiKey=${apiKey} apiSecret=${apiSecret}"
        if ((config[apiKey]) && (config[apiKey].secret == apiSecret)) {
            return true
        }
    }
    return false
}

def isSecure(HttpServerRequest request, config) {
    // app is running on https, so it's secure
    if (config.https.enabled == true) return true
    // heroku adds header "X-Forwarded-Proto"="https"
    def forwardedProto = request.headers['X-Forwarded-Proto']
    return "https".equalsIgnoreCase(forwardedProto)
}

def title(String message) {
    logger.info ""
    logger.info message
    logger.info ('-' * message.size())
}

def printRequestHeaders(request) {
    logger.info "Request headers:"
    request.headers.each { k, v ->
        logger.info " - ${k} : ${v}"
    }
}

def printAuth(auth) {
    logger.info 'Users:'
    auth.users.each { user, data ->
        logger.info "- name=${user} password=${data.password}"
    }

    logger.info 'API:'
    auth.api.each { api, data ->
        logger.info "- key=${api} user=${data.user} secret=${data.secret}"
    }
}

def resourceNotFound(req) {
    req.response.statusCode = 404
    req.response.statusMessage = PAGE_NOT_FOUND_MESSAGE
    req.response.end PAGE_NOT_FOUND_MESSAGE
}
