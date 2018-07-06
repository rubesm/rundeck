package rundeck.controllers

import com.dtolabs.rundeck.core.common.Framework
import grails.test.mixin.TestFor
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.plugins.testing.GrailsMockMultipartFile
import rundeck.services.FrameworkService
import spock.lang.Specification

class PluginControllerSpec extends Specification implements ControllerUnitTest<PluginController> {

    static final String PLUGIN_FILE = "rundeck-ui-plugin-examples-1.0-plugin.zip"

    File uploadTestBaseDir = File.createTempDir()
    File uploadTestTargetDir = File.createTempDir()

    def setup() {
        controller.frameworkService = Mock(FrameworkService)
        controller.rundeckFramework = Mock(Framework) {
            getBaseDir() >> uploadTestBaseDir
            getLibextDir() >> uploadTestTargetDir
        }
    }

    void "upload plugin no file specified"() {
        when:
        controller.uploadPlugin()

        then:
        1 * controller.frameworkService.getAuthContextForSubject(_)
        1 * controller.frameworkService.authorizeApplicationResourceType(_,_,_) >> true
        response.redirectUrl == "/menu/plugins"
        flash.errors == ["plugin.error.missing.upload.file"]
    }

    void "install plugin no plugin url specified"() {
        when:
        controller.installPlugin()

        then:
        1 * controller.frameworkService.getAuthContextForSubject(_)
        1 * controller.frameworkService.authorizeApplicationResourceType(_,_,_) >> true
        response.redirectUrl == "/menu/plugins"
        flash.errors == ["plugin.error.missing.url"]
    }

    void "upload plugin"() {
        File uploaded = new File(uploadTestTargetDir,PLUGIN_FILE)

        when:
        !uploaded.exists()
        def pluginInputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(PLUGIN_FILE)
        request.addFile(new GrailsMockMultipartFile("pluginFile",PLUGIN_FILE,"application/octet-stream",pluginInputStream))
        controller.uploadPlugin()

        then:
        1 * controller.frameworkService.getAuthContextForSubject(_)
        1 * controller.frameworkService.authorizeApplicationResourceType(_,_,_) >> true
        response.redirectUrl == "/menu/plugins"
        flash.installSuccess
        uploaded.exists()

        cleanup:
        uploaded.delete()
    }

    void "install plugin"() {
        File installed = new File(uploadTestTargetDir,PLUGIN_FILE)

        when:
        !installed.exists()
        def pluginUrl = Thread.currentThread().getContextClassLoader().getResource(PLUGIN_FILE)
        params.pluginUrl = pluginUrl.toString()
        controller.installPlugin()

        then:
        1 * controller.frameworkService.getAuthContextForSubject(_)
        1 * controller.frameworkService.authorizeApplicationResourceType(_,_,_) >> true
        response.redirectUrl == "/menu/plugins"
        flash.installSuccess
        installed.exists()

        cleanup:
        installed.delete()
    }

    void "unauthorized install plugin fails"() {
        when:
        def pluginUrl = Thread.currentThread().getContextClassLoader().getResource(PLUGIN_FILE)
        params.pluginUrl = pluginUrl.toString()
        controller.installPlugin()

        then:
        1 * controller.frameworkService.getAuthContextForSubject(_)
        1 * controller.frameworkService.authorizeApplicationResourceType(_,_,_) >> false
        response.redirectUrl == "/menu/plugins"
        flash.errors == ["request.error.unauthorized.title"]
    }
}
