package com.serverdash.app.parsing

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.*
import org.junit.Test

class McpParsingTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `parse mcp servers from JSON`() {
        val jsonStr = """
        {
            "mcpServers": {
                "filesystem": {
                    "command": "npx",
                    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/home/user/projects"],
                    "env": {"NODE_ENV": "production"}
                },
                "github": {
                    "command": "npx",
                    "args": ["-y", "@modelcontextprotocol/server-github"]
                }
            }
        }
        """.trimIndent()

        val root = Json.parseToJsonElement(jsonStr).jsonObject
        val mcpObj = root["mcpServers"]?.jsonObject
        assertThat(mcpObj).isNotNull()
        assertThat(mcpObj!!.entries).hasSize(2)

        val fs = mcpObj["filesystem"]?.jsonObject!!
        assertThat(fs["command"]?.jsonPrimitive?.content).isEqualTo("npx")
        assertThat(fs["args"]?.jsonArray?.map { it.jsonPrimitive.content })
            .containsExactly("-y", "@modelcontextprotocol/server-filesystem", "/home/user/projects")
        assertThat(fs["env"]?.jsonObject?.get("NODE_ENV")?.jsonPrimitive?.content)
            .isEqualTo("production")
    }

    @Test
    fun `parse empty mcp servers`() {
        val jsonStr = "{}"
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        val mcpObj = root["mcpServers"]?.jsonObject
        assertThat(mcpObj).isNull()
    }

    @Test
    fun `parse mcp servers without env`() {
        val jsonStr = """{"mcpServers": {"test": {"command": "node", "args": ["server.js"]}}}"""
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        val mcpObj = root["mcpServers"]?.jsonObject!!
        val server = mcpObj["test"]?.jsonObject!!
        assertThat(server["env"]).isNull()
        assertThat(server["command"]?.jsonPrimitive?.content).isEqualTo("node")
    }

    @Test
    fun `save mcp server to JSON`() {
        val root = mutableMapOf<String, JsonElement>()
        val mcpObj = mutableMapOf<String, JsonElement>()

        val serverObj = buildJsonObject {
            put("command", "npx")
            putJsonArray("args") {
                add("-y")
                add("@some/server")
            }
            putJsonObject("env") {
                put("API_KEY", "test123")
            }
        }
        mcpObj["myserver"] = serverObj
        root["mcpServers"] = JsonObject(mcpObj)

        val result = json.encodeToString(JsonObject.serializer(), JsonObject(root))
        assertThat(result).contains("myserver")
        assertThat(result).contains("npx")
        assertThat(result).contains("API_KEY")
    }

    @Test
    fun `remove mcp server from JSON`() {
        val mcpObj = mutableMapOf<String, JsonElement>(
            "keep" to buildJsonObject { put("command", "a") },
            "remove" to buildJsonObject { put("command", "b") }
        )
        mcpObj.remove("remove")
        assertThat(mcpObj).hasSize(1)
        assertThat(mcpObj.containsKey("keep")).isTrue()
        assertThat(mcpObj.containsKey("remove")).isFalse()
    }

    @Test
    fun `parse overview sections`() {
        val output = """===DU===
156M
===STATS===
{"total_tokens": 12345}
===PROJECTS===
5
===PLANS===
3
===SESSIONS===
12
===PLUGINS===
["plugin-a", "plugin-b"]
===SKILLS===
skill1.md
skill2.md
===HOOKS===
pre-commit.sh
post-push.sh"""

        val sections = output.split("===DU===", "===STATS===", "===PROJECTS===", "===PLANS===", "===SESSIONS===", "===PLUGINS===", "===SKILLS===", "===HOOKS===")

        assertThat(sections.getOrNull(1)?.trim()).isEqualTo("156M")
        assertThat(sections.getOrNull(2)?.trim()).isEqualTo("{\"total_tokens\": 12345}")
        assertThat(sections.getOrNull(3)?.trim()?.toIntOrNull()).isEqualTo(5)
        assertThat(sections.getOrNull(4)?.trim()?.toIntOrNull()).isEqualTo(3)
        assertThat(sections.getOrNull(5)?.trim()?.toIntOrNull()).isEqualTo(12)

        val pluginsJson = sections.getOrNull(6)?.trim() ?: ""
        val plugins = Json.parseToJsonElement(pluginsJson).jsonArray.map { it.jsonPrimitive.content }
        assertThat(plugins).containsExactly("plugin-a", "plugin-b")

        val skills = sections.getOrNull(7)?.trim()?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        assertThat(skills).containsExactly("skill1.md", "skill2.md")

        val hooks = sections.getOrNull(8)?.trim()?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        assertThat(hooks).containsExactly("pre-commit.sh", "post-push.sh")
    }

    @Test
    fun `parse overview with empty sections`() {
        val output = "===DU===\n===STATS===\n===PROJECTS===\n0\n===PLANS===\n0\n===SESSIONS===\n0\n===PLUGINS===\n===SKILLS===\n===HOOKS==="
        val sections = output.split("===DU===", "===STATS===", "===PROJECTS===", "===PLANS===", "===SESSIONS===", "===PLUGINS===", "===SKILLS===", "===HOOKS===")

        assertThat(sections.getOrNull(1)?.trim()).isEmpty()
        assertThat(sections.getOrNull(3)?.trim()?.toIntOrNull()).isEqualTo(0)
    }

    @Test
    fun `parse project listing`() {
        val output = """
-home-matt-fleet|3|Y
-home-matt-webapp|1|N
-root-scripts|0|N
        """.trimIndent()

        val projects = output.lines()
            .filter { it.contains("|") }
            .map { line ->
                val parts = line.split("|", limit = 3)
                val rawName = parts[0].trim()
                val displayName = rawName.replace("-", "/").let {
                    if (it.startsWith("/")) it else "/$it"
                }
                Triple(displayName, parts[1].trim().toInt(), parts[2].trim() == "Y")
            }

        assertThat(projects).hasSize(3)
        assertThat(projects[0].first).isEqualTo("/home/matt/fleet")
        assertThat(projects[0].second).isEqualTo(3)
        assertThat(projects[0].third).isTrue()
        assertThat(projects[1].third).isFalse()
    }

    @Test
    fun `settings diff computation`() {
        val origObj = Json.parseToJsonElement("""{"model": "opus", "verbose": false}""").jsonObject
        val newObj = Json.parseToJsonElement("""{"model": "sonnet", "fastMode": true}""").jsonObject

        val allKeys = (origObj.keys + newObj.keys).toSet()
        val changes = mutableListOf<String>()

        for (key in allKeys) {
            val old = origObj[key]
            val new = newObj[key]
            when {
                old == null && new != null -> changes.add("added:$key")
                old != null && new == null -> changes.add("removed:$key")
                old != new -> changes.add("changed:$key")
            }
        }

        assertThat(changes).containsExactly("changed:model", "removed:verbose", "added:fastMode")
    }

    @Test
    fun `parse installed plugins JSON array of strings`() {
        val pluginsJson = """["filesystem", "github", "slack"]"""
        val plugins = Json.parseToJsonElement(pluginsJson).jsonArray.map { it.jsonPrimitive.content }
        assertThat(plugins).containsExactly("filesystem", "github", "slack")
    }

    @Test
    fun `parse installed plugins JSON array of objects`() {
        val pluginsJson = """[{"name": "filesystem"}, {"name": "github"}]"""
        val plugins = Json.parseToJsonElement(pluginsJson).jsonArray.map { element ->
            if (element is JsonPrimitive) element.content
            else element.jsonObject["name"]?.jsonPrimitive?.content ?: element.toString()
        }
        assertThat(plugins).containsExactly("filesystem", "github")
    }

    @Test
    fun `parse invalid plugins JSON returns empty`() {
        val pluginsJson = "not json"
        val plugins = try {
            Json.parseToJsonElement(pluginsJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) { emptyList() }
        assertThat(plugins).isEmpty()
    }
}
