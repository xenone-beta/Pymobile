package dev.xenolab.pymobile.project

import androidx.annotation.StringRes
import dev.xenolab.pymobile.R
import java.io.File

/** A starting point for a new project: a few files, written into a new folder. */
class Template(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    /** Path inside the project to file contents. The first entry is opened after creation. */
    val files: List<Pair<String, String>>,
)

object Templates {

    private val script = Template(
        "script", R.string.tpl_script, R.string.tpl_script_desc,
        listOf("main.py" to ProjectFiles.STARTER_SOURCE),
    )

    private val cli = Template(
        "cli", R.string.tpl_cli, R.string.tpl_cli_desc,
        listOf(
            "main.py" to """
                |import argparse
                |
                |
                |def main() -> None:
                |    parser = argparse.ArgumentParser(description="What this program does")
                |    parser.add_argument("name", nargs="?", default="world", help="who to greet")
                |    parser.add_argument("-n", "--times", type=int, default=1, help="how many times")
                |    args = parser.parse_args()
                |
                |    for _ in range(args.times):
                |        print(f"Hello, {args.name}!")
                |
                |
                |if __name__ == "__main__":
                |    main()
                |""".trimMargin(),
        ),
    )

    private val bot = Template(
        "bot", R.string.tpl_bot, R.string.tpl_bot_desc,
        listOf(
            "bot.py" to """
                |import json
                |import os
                |import time
                |import urllib.parse
                |import urllib.request
                |
                |# Put the token from @BotFather into the TELEGRAM_TOKEN variable (Run settings), or paste it here.
                |TOKEN = os.environ.get("TELEGRAM_TOKEN", "")
                |API = f"https://api.telegram.org/bot{TOKEN}/"
                |
                |
                |def call(method: str, **params):
                |    data = urllib.parse.urlencode(params).encode()
                |    with urllib.request.urlopen(API + method, data=data, timeout=60) as response:
                |        return json.load(response)["result"]
                |
                |
                |def handle(message: dict) -> None:
                |    text = message.get("text", "")
                |    chat = message["chat"]["id"]
                |    if text == "/start":
                |        call("sendMessage", chat_id=chat, text="Hi! Send me anything and I will repeat it.")
                |    elif text:
                |        call("sendMessage", chat_id=chat, text=text)
                |
                |
                |def main() -> None:
                |    if not TOKEN:
                |        raise SystemExit("Set TELEGRAM_TOKEN first.")
                |    offset = 0
                |    print("Bot is running. Stop it with the Stop button.")
                |    while True:
                |        try:
                |            for update in call("getUpdates", offset=offset, timeout=30):
                |                offset = update["update_id"] + 1
                |                if "message" in update:
                |                    handle(update["message"])
                |        except Exception as error:  # network hiccups should not stop the bot
                |            print("error:", error)
                |            time.sleep(3)
                |
                |
                |if __name__ == "__main__":
                |    main()
                |""".trimMargin(),
        ),
    )

    private val web = Template(
        "web", R.string.tpl_web, R.string.tpl_web_desc,
        listOf(
            "server.py" to """
                |import json
                |from http.server import BaseHTTPRequestHandler, HTTPServer
                |
                |PORT = 8000
                |
                |
                |class Handler(BaseHTTPRequestHandler):
                |    def do_GET(self):
                |        if self.path == "/api":
                |            body = json.dumps({"hello": "world"}).encode()
                |            content_type = "application/json"
                |        else:
                |            body = b"<h1>Hello from Pymobile</h1>"
                |            content_type = "text/html; charset=utf-8"
                |        self.send_response(200)
                |        self.send_header("Content-Type", content_type)
                |        self.send_header("Content-Length", str(len(body)))
                |        self.end_headers()
                |        self.wfile.write(body)
                |
                |
                |if __name__ == "__main__":
                |    print(f"Open http://localhost:{PORT} in a browser")
                |    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
                |""".trimMargin(),
        ),
    )

    private val flask = Template(
        "flask", R.string.tpl_flask, R.string.tpl_flask_desc,
        listOf(
            "app.py" to """
                |from flask import Flask, jsonify
                |
                |app = Flask(__name__)
                |
                |
                |@app.route("/")
                |def index():
                |    return "<h1>Hello from Flask</h1>"
                |
                |
                |@app.route("/api")
                |def api():
                |    return jsonify(hello="world")
                |
                |
                |if __name__ == "__main__":
                |    app.run(host="0.0.0.0", port=5000)
                |""".trimMargin(),
            "requirements.txt" to "flask\n",
        ),
    )

    private val tests = Template(
        "tests", R.string.tpl_tests, R.string.tpl_tests_desc,
        listOf(
            "calc.py" to """
                |def add(a, b):
                |    return a + b
                |
                |
                |def divide(a, b):
                |    if b == 0:
                |        raise ValueError("division by zero")
                |    return a / b
                |""".trimMargin(),
            "test_calc.py" to """
                |import unittest
                |
                |from calc import add, divide
                |
                |
                |class CalcTests(unittest.TestCase):
                |    def test_add(self):
                |        self.assertEqual(add(2, 3), 5)
                |
                |    def test_divide(self):
                |        self.assertEqual(divide(6, 3), 2)
                |
                |    def test_divide_by_zero(self):
                |        with self.assertRaises(ValueError):
                |            divide(1, 0)
                |
                |
                |if __name__ == "__main__":
                |    unittest.main()
                |""".trimMargin(),
        ),
    )

    val all: List<Template> = listOf(script, cli, bot, web, flask, tests)

    fun byId(id: String): Template = all.firstOrNull { it.id == id } ?: script

    /** Makes the folder [name] below [root] and writes the files of [template] into it. */
    fun create(root: File, name: String, template: Template): FileOp {
        val made = ProjectFiles.create(root, name, folder = true)
        val dir = made.file ?: return made
        return try {
            for ((path, text) in template.files) File(dir, path).writeText(text, Charsets.UTF_8)
            made
        } catch (_: java.io.IOException) {
            FileOp(null, FileError.FAILED)
        }
    }
}
