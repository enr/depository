package util

import groovy.json.*

/*
 * Load configuration from a json file returning a map
 */
def loadConfiguration(String path) {
    def config
    def configFile = path as File
    if (configFile.exists()) {
        config = new JsonSlurper().parseText(configFile.text)
    }
    return config
}

void error(String message) {
    def prefix = (isAnsiEnabled() ? "\u001B[31m" : "")
    def suffix = (isAnsiEnabled() ? "\u001B[0m" : "")
    println "${prefix}${message}${suffix}"
}

boolean isAnsiEnabled() {
    boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("win") >= 0
    String ansicon = System.getenv('ANSICON')
    return (!isWindows || ansicon)
}

boolean isURLValid(input) {
    if (input == null || (!input.startsWith('http://') && !input.startsWith('https://'))) {
        return false
    }
    try {
        new URL(input)
        return true
    } catch (MalformedURLException e) {
        return false
    }
}

void printConfiguration(app) {
    println "Application configuration"
    println "-------------------------"
    app.each { k, v ->
        println "$k=$v"
    }
    println "http  port         ${app.http.port}"
    println "https enabled      ${app.https.enabled}"
    println "      port         ${app.https.port}"
    println "      keystore     ${app.https.keystore.path}"
    println "                   ${app.https.keystore.password}"
    println "downloads enabled  ${app.downloads.enabled}"
    println "          path     ${app.downloads.path}"
    println "auth enabled       ${app.auth.enabled}"
    println "     mode          ${app.auth.mode}"
    println "data users         ${app.data.users}"
    println ""
}

String tostr(/*wslite.rest.Response*/ response) {
    String str = ""
    if (response.text) {
        str = response.text
    } else if (response.data) {
        str = new String(response.data, "UTF-8")
    }
    return str  //.trim()
}
