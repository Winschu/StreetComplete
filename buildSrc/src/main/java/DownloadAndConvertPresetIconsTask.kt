import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.max

open class DownloadAndConvertPresetIconsTask : DefaultTask() {
    @get:Input var targetDir: String? = null
    @get:Input var version: String? = null
    @get:Input var iconSize: Int = 14
    @get:Input var transformName: (String) -> String = { it }

    @TaskAction fun run() {
        val targetDir = targetDir ?: return
        val version = version ?: return

        val icons = getIconNames(version)
        for (icon in icons) {
            val url = getDownloadUrl(icon) ?: continue

            val targetFile = File("$targetDir/${ transformName(icon) }.xml")
            targetFile.parentFile.mkdirs()

            try {
                URL(url).openStream().use { input ->
                    val factory = DocumentBuilderFactory.newInstance()
                    factory.isIgnoringComments = true
                    val svg = factory.newDocumentBuilder().parse(input)

                    val drawable = createAndroidDrawable(svg)

                    writeXml(drawable, targetFile)
                }
            } catch (e: IOException) {
                println("$icon not found")
            } catch (e: IllegalArgumentException) {
                println("$icon not supported: " + e.message)
            }
        }
    }

    private fun createAndroidDrawable(svg: Document): Document {
        val drawable = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

        var root: Element? = null
        for (i in 0..svg.childNodes.length) {
            val node = svg.childNodes.item(i)
            if (node is Element) {
                root = node
                break
            }
        }

        require(root != null) { "No root node found" }
        require(root.tagName  == "svg") { "Root must be <svg>" }

        val viewBox = root.getAttribute("viewBox")
        require(viewBox.isNotEmpty()) { "viewBox is missing"}
        val rect = viewBox.split(' ')

        require(rect.size == 4) { "Expected viewBox to have 4 values" }
        require(rect[0] == "0") { "unsupported viewBox x" }
        require(rect[1] == "0") { "unsupported viewBox y" }
        val width = rect[2]
        val height = rect[3]

        val x = root.getAttribute("x")
        require(x == "" || x == "0" || x == "0px") { "x must be 0" }
        val y = root.getAttribute("y")
        require(y == "" || y == "0" || y == "0px") { "y must be 0"}

        val width2 = root.getAttribute("width")
        val height2 = root.getAttribute("height")

        require(width2 == "" || width2 == width) { "expect viewBox width and width to be identical" }
        require(height2 == "" || height2 == height) { "expect viewBox height and height to be identical" }

        val vector = drawable.createElement("vector")
        vector.setAttribute("xmlns:android", "http://schemas.android.com/apk/res/android")
        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val size = max(widthF, heightF)
        val iconWidth = iconSize * widthF / size
        val iconHeight = iconSize * heightF / size
        vector.setAttribute("android:width", "${iconWidth}dp")
        vector.setAttribute("android:height", "${iconHeight}dp")
        vector.setAttribute("android:viewportWidth", width)
        vector.setAttribute("android:viewportHeight", height)
        vector.setAttribute("android:tint", "?attr/colorControlNormal")
        drawable.appendChild(vector)

        for (i in 0 until root.childNodes.length) {
            val element = root.childNodes.item(i) as? Element ?: continue
            require(element.tagName == "path") { "Only paths are supported" }
            for (a in 0 until element.attributes.length) {
                val attr = element.attributes.item(a) as Attr
                require (attr.name in supportedPathAttributes) { "path attribute '${attr.name}' not supported" }
            }
            val d = element.getAttribute("d")
            require(d != "") { "no path defined" }

            val path = drawable.createElement("path")
            path.setAttribute("android:fillColor", "@android:color/white")
            path.setAttribute("android:pathData", d)
            vector.appendChild(path)
        }

        return drawable
    }

    private val supportedPathAttributes = setOf("d", "id")

    private fun writeXml(xml: Document, targetFile: File) {
        FileOutputStream(targetFile).use { output ->
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            val source = DOMSource(xml)
            val result = StreamResult(output)
            transformer.transform(source, result)
        }
    }

    private fun getIconNames(version: String): Set<String> {
        val presetsUrl = URL("https://raw.githubusercontent.com/openstreetmap/id-tagging-schema/$version/dist/presets.json")
        val presetsJson = Parser.default().parse(presetsUrl.openStream()) as JsonObject
        val icons = HashSet<String>()
        for (value in presetsJson.values) {
            val preset = value as? JsonObject ?: continue
            val icon = preset["icon"] as? String ?: continue
            icons.add(icon)
        }
        return icons
    }

    private fun getDownloadUrl(icon: String): String? {
        val prefix = icon.substringBefore('-', "")
        val file = icon.substringAfter('-')
        return when(prefix) {
            "iD" -> "https://raw.githubusercontent.com/openstreetmap/iD/develop/svg/iD-sprite/presets/$file.svg"
            "maki" -> "https://raw.githubusercontent.com/mapbox/maki/main/icons/$file.svg"
            "temaki" -> "https://raw.githubusercontent.com/ideditor/temaki/main/icons/$file.svg"
            "fas" -> "https://raw.githubusercontent.com/FortAwesome/Font-Awesome/master/svgs/solid/$file.svg"
            "far" -> "https://raw.githubusercontent.com/FortAwesome/Font-Awesome/master/svgs/regular/$file.svg"
            "fab" -> "https://raw.githubusercontent.com/FortAwesome/Font-Awesome/master/svgs/brands/$file.svg"
            else -> null
        }
    }
}
