package rundeck.controllers

import com.dtolabs.rundeck.app.support.PluginResourceReq
import com.dtolabs.rundeck.core.authorization.AuthContext
import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.plugins.PluginValidator
import com.dtolabs.rundeck.server.authorization.AuthConstants
import grails.converters.JSON
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.RequestContextUtils
import rundeck.services.UiPluginService

class PluginController extends ControllerBase {
    private static final String RELATIVE_PLUGIN_UPLOAD_DIR = "var/tmp/pluginUpload"
    UiPluginService uiPluginService
    Framework rundeckFramework
    def frameworkService

    def pluginIcon(PluginResourceReq resourceReq) {
        if (resourceReq.hasErrors()) {
            request.errors = resourceReq.errors
            response.status = 400
            return render(view: '/common/error')
        }
        def profile = uiPluginService.getProfileFor(resourceReq.service, resourceReq.name)
        if (!profile.icon) {
            response.status = 404
            return render(view: '/404')
        }
        resourceReq.path = profile.icon
        pluginFile(resourceReq)
    }

    def pluginFile(PluginResourceReq resourceReq) {
        if (!resourceReq.path) {
            resourceReq.errors.rejectValue('path', 'blank')
        }
        if (resourceReq.hasErrors()) {
            request.errors = resourceReq.errors
            response.status = 400
            return render(view: '/common/error')
        }
        def istream = uiPluginService.openResourceForPlugin(resourceReq.service, resourceReq.name, resourceReq.path)
        if (null == istream) {
            response.status = 404
            return render(view: '/404')
        }
        try {
            def format = servletContext.getMimeType(resourceReq.path)

            response.contentType = format
            response.outputStream << istream.bytes
            response.flushBuffer()
        }finally{
            istream.close()
        }
    }

    def pluginMessages(PluginResourceReq resourceReq) {
        if (!resourceReq.path) {
            resourceReq.errors.rejectValue('path', 'blank')
        }
        if (resourceReq.hasErrors()) {
            request.errors = resourceReq.errors
            response.status = 400
            return render(view: '/common/error')
        }

        List<Locale> locales = [RequestContextUtils.getLocale(request)]

        def stem = resourceReq.path.lastIndexOf(".") >= 0 ? resourceReq.path.substring(
                0,
                resourceReq.path.lastIndexOf(".")
        ) : resourceReq.path

        def suffix = resourceReq.path.lastIndexOf(".") >= 0 ? resourceReq.path.substring(
                resourceReq.path.lastIndexOf(".")
        ) : ''

        if (!locales) {
            locales = [Locale.getDefault(), null]//defaults
        } else {
            locales.add(Locale.getDefault())
            locales.add(null)
        }

        InputStream istream
        List<String> langs = locales.collect { Locale locale ->
            locale ? [
                    locale.toLanguageTag(),
                    locale.language
            ] : null
        }.flatten()

        for (String lang : langs) {
            def newpath = stem + (lang ? '_' + lang.replaceAll('-', '_') : '') + suffix
            istream = uiPluginService.openResourceForPlugin(resourceReq.service, resourceReq.name, newpath)
            if (istream != null) {
                break
            }
        }

        if (null == istream) {
            response.status = 404
            return render(view: '/404')
        }
        if (resourceReq.path.endsWith(".properties") && response.format == 'json') {
            //parse java .properties content and emit as json
            def jprops = new Properties()

            try {
                def reader = new InputStreamReader(istream, 'UTF-8')
                jprops.load(reader)
            } catch (IOException e) {
                response.status = 500
                return respond([status: 500])
            } finally {
                istream.close()
            }
            return render(contentType: 'application/json', text: new HashMap(jprops) as JSON)
        }

        try {
            def format = servletContext.getMimeType(resourceReq.path)

            response.contentType = format
            response.outputStream << istream.bytes
            response.flushBuffer()
        }finally{
            istream.close()
        }
    }

    def uploadPlugin() {
        AuthContext authContext = frameworkService.getAuthContextForSubject(session.subject)
        boolean authorized = frameworkService.authorizeApplicationResourceType(authContext,
                                                          "system",
                                                          AuthConstants.ACTION_ADMIN)
        if (!authorized) {
            flash.errors = ["request.error.unauthorized.title"]
            redirectToPluginMenu()
            return
        }
        if(!params.pluginFile || params.pluginFile.isEmpty()) {
            flash.errors = ["plugin.error.missing.upload.file"]
            redirectToPluginMenu()
            return
        }
        ensureUploadLocation()
        File tmpFile = new File(rundeckFramework.baseDir,RELATIVE_PLUGIN_UPLOAD_DIR+"/"+params.pluginFile.originalFilename)
        if(tmpFile.exists()) tmpFile.delete()
        tmpFile << ((MultipartFile)params.pluginFile).inputStream
        flash.errors = validateAndCopyPlugin(params.pluginFile.originalFilename, tmpFile)
        tmpFile.delete()
        redirectToPluginMenu()
    }

    def installPlugin() {
        AuthContext authContext = frameworkService.getAuthContextForSubject(session.subject)
        boolean authorized = frameworkService.authorizeApplicationResourceType(authContext,
                                                                               "system",
                                                                               AuthConstants.ACTION_ADMIN)
        if (!authorized) {
            flash.errors = ["request.error.unauthorized.title"]
            redirectToPluginMenu()
            return
        }
        if(!params.pluginUrl) {
            flash.errors = ["plugin.error.missing.url"]
            redirectToPluginMenu()
            return
        }
        if(!params.pluginUrl.contains("/")) {
            flash.errors = ["plugin.error.invalid.url"]
            redirectToPluginMenu()
            return
        }
        def parts = params.pluginUrl.split("/")
        String urlString = params.pluginUrl.startsWith("/") ? "file:"+params.pluginUrl : params.pluginUrl

        ensureUploadLocation()
        File tmpFile = new File(rundeckFramework.baseDir,RELATIVE_PLUGIN_UPLOAD_DIR+"/"+parts.last())
        if(tmpFile.exists()) tmpFile.delete()
        try {
            URI.create(urlString).toURL().withInputStream { inputStream ->
                tmpFile << inputStream
            }
        } catch(Exception ex) {
            flash.errors = ["Failed to fetch plugin from URL. Error: ${ex.message}"]
            redirectToPluginMenu()
            return
        }
        flash.errors = validateAndCopyPlugin(parts.last(),tmpFile)
        tmpFile.delete()
        redirectToPluginMenu()
    }

    private def validateAndCopyPlugin(String pluginName, File tmpPluginFile) {
        def errors = []
        File newPlugin = new File(rundeckFramework.libextDir,pluginName)
        if(newPlugin.exists()) {
            errors.add("The plugin ${params.pluginFile.originalFilename} already exists")
            return errors
        }
        if(!PluginValidator.validate(tmpPluginFile)) {
            errors.add("plugin.error.invalid.plugin")
        } else {
            tmpPluginFile.withInputStream { inStream ->
                newPlugin << inStream
            }
            flash.installSuccess = true
        }
        return errors
    }

    private redirectToPluginMenu() {
        redirect controller:"menu",action:"plugins"
    }

    private def ensureUploadLocation() {
        File uploadDir = new File(rundeckFramework.baseDir,RELATIVE_PLUGIN_UPLOAD_DIR)
        if(!uploadDir.exists()) {
            uploadDir.mkdirs()
        }
    }
}
