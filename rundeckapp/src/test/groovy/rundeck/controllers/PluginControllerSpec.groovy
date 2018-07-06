package rundeck.controllers

import com.dtolabs.rundeck.core.common.Framework
import grails.test.mixin.TestFor
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.plugins.testing.GrailsMockMultipartFile
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.web.ControllerUnitTestMixin} for usage instructions
 */
class PluginControllerSpec extends Specification implements ControllerUnitTest<PluginController> {

    static final String PLUGIN_FILE = "rundeck-ui-plugin-examples-1.0-plugin.zip"

    File uploadTestBaseDir = File.createTempDir()
    File uploadTestTargetDir = File.createTempDir()

    def setup() {
        controller.rundeckFramework = Mock(Framework) {
            getBaseDir() >> uploadTestBaseDir
            getLibextDir() >> uploadTestTargetDir
        }
    }

    def cleanup() {
    }

    void "upload plugin no file specified"() {
        when:
        controller.uploadPlugin()

        then:
        response.redirectUrl == "/menu/plugins"
        flash.errors == ["A plugin file must be specified"]
    }

    void "install plugin no plugin url specified"() {
        when:
        controller.installPlugin()

        then:
        response.redirectUrl == "/menu/plugins"
        flash.errors == ["The plugin URL is required"]
    }

    void "upload plugin"() {
        File uploaded = new File(uploadTestTargetDir,PLUGIN_FILE)

        when:
        !uploaded.exists()
        def pluginInputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(PLUGIN_FILE)
        request.addFile(new GrailsMockMultipartFile("pluginFile",PLUGIN_FILE,"application/octet-stream",pluginInputStream))
        controller.uploadPlugin()

        then:
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
        response.redirectUrl == "/menu/plugins"
        flash.installSuccess
        installed.exists()

        cleanup:
        installed.delete()
    }
}
