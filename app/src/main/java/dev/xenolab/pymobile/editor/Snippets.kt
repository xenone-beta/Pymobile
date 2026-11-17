package dev.xenolab.pymobile.editor

import org.json.JSONArray
import org.json.JSONObject

/** A piece of code to insert. [body] may contain [Snippets.CURSOR] once, where the caret should end up. */
data class Snippet(val trigger: String, val title: String, val body: String, val builtin: Boolean = false)

object Snippets {

    const val CURSOR = "\$0"

    val builtin: List<Snippet> = listOf(
        Snippet("ifmain", "if __name__ == \"__main__\"", "if __name__ == \"__main__\":\n    \$0", true),
        Snippet("def", "Function", "def \$0():\n    pass\n", true),
        Snippet("class", "Class", "class \$0:\n    def __init__(self):\n        pass\n", true),
        Snippet("for", "for loop", "for i in range(\$0):\n    pass\n", true),
        Snippet("while", "while loop", "while \$0:\n    pass\n", true),
        Snippet("try", "try / except", "try:\n    \$0\nexcept Exception as error:\n    print(error)\n", true),
        Snippet("with", "Read a file", "with open(\"\$0\", encoding=\"utf-8\") as f:\n    text = f.read()\n", true),
        Snippet("json", "Read JSON", "import json\n\nwith open(\"\$0\", encoding=\"utf-8\") as f:\n    data = json.load(f)\n", true),
        Snippet("comp", "List comprehension", "[\$0 for x in items]", true),
        Snippet("dataclass", "Dataclass", "from dataclasses import dataclass\n\n\n@dataclass\nclass \$0:\n    name: str\n    count: int = 0\n", true),
        Snippet("argparse", "Command line arguments", "import argparse\n\nparser = argparse.ArgumentParser()\nparser.add_argument(\"\$0\")\nargs = parser.parse_args()\n", true),
        Snippet("http", "Download a page", "import urllib.request\n\nwith urllib.request.urlopen(\"\$0\") as response:\n    print(response.read().decode())\n", true),
        Snippet("async", "asyncio program", "import asyncio\n\n\nasync def main():\n    \$0\n\n\nasyncio.run(main())\n", true),
        Snippet("log", "Logging", "import logging\n\nlogging.basicConfig(level=logging.INFO)\nlog = logging.getLogger(__name__)\n\$0", true),
        Snippet("input", "Read numbers", "numbers = [int(x) for x in input(\"\$0\").split()]\n", true),
    )

    /**
     * The text to insert for [body], and where the caret goes inside it. Every line after the first
     * gets [indent], so a snippet inserted in a nested block stays aligned.
     */
    fun render(body: String, indent: String): Pair<String, Int> {
        val at = body.indexOf(CURSOR)
        val before = if (at < 0) body else body.substring(0, at)
        val after = if (at < 0) "" else body.substring(at + CURSOR.length).replace(CURSOR, "")
        val b = before.replace("\n", "\n$indent")
        val a = after.replace("\n", "\n$indent")
        return (b + a) to b.length
    }

    fun toJson(list: List<Snippet>): String {
        val array = JSONArray()
        for (s in list) array.put(JSONObject().put("trigger", s.trigger).put("title", s.title).put("body", s.body))
        return array.toString()
    }

    fun fromJson(text: String): List<Snippet> = try {
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val body = o.optString("body")
            if (body.isEmpty()) null else Snippet(o.optString("trigger"), o.optString("title").ifEmpty { o.optString("trigger") }, body)
        }
    } catch (_: org.json.JSONException) {
        emptyList()
    }
}
