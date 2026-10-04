public class ConverterTest {
    public static void main(String[] args) {
        String html = ChatHtmlConverter.convert("\uFEFFUser:\r\n質問\r\n\r\nAssistant:\r\n# 見出し\r\n**太字** と `code`\r\n\r\n```html\r\n<script>alert(1)</script>\r\nUser:\r\nAssistant:\r\n```\r\n\r\n続き");
        require(html.contains("<h1>見出し</h1>"), "heading");
        require(html.contains("<strong>太字</strong>"), "bold");
        require(html.contains("<code class=\"inline-code\">code</code>"), "inline code");
        require(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;\nUser:\nAssistant:"), "escaped code and role markers");
        require(!html.contains("<script>"), "no executable markup");
        require(html.split("class=\"message-row ", -1).length == 3, "two messages");
        String aliases = ChatHtmlConverter.convert("あなた:\n日本語\nAI:\n返答\n自分:\n質問\nChatGPT:\n回答");
        require(aliases.split("class=\"message-row ", -1).length == 5, "Japanese markers");
        String escaped = ChatHtmlConverter.convert("User:\n<img src=x onerror=alert(1)> & \" '\nAssistant:\n**<b>** `</code>`");
        require(!escaped.contains("<img"), "plain text escaping");
        require(escaped.contains("&lt;/code&gt;"), "inline code escaping");
        require(ChatHtmlConverter.convert("Assistant:\n```\nunclosed").contains("<pre><code>unclosed</code></pre>"), "unclosed fence");
        String markdown = ChatHtmlConverter.convert("説明です。\n\n```java\nTask task = new Job();\n```\n\n"
                + "| コード | 意味 |\n|---|---|\n"
                + "| `Task task` | `run()` を呼べる |\n"
                + "| `new Job()` | **Job** を作る |\n"
                + "| `task.run()` | 実体の `run()` を実行 |\n\n"
                + "次の表です。\n\n| 定義 | 実行されるもの |\n|---|---|\n"
                + "| そのまま | `Job.run()` |\n"
                + "| `Job` のメソッドを削除 | 継承した `Base.run()` |\n\n終わりです。");
        require(occurrences(markdown, "<table>") == 2, "markerless markdown renders both compact tables");
        require(occurrences(markdown, "class=\"message-row ") == 1, "markerless markdown is one message");
        require(markdown.contains("<th scope=\"col\">コード</th><th scope=\"col\">意味</th>"), "compact table headers");
        require(markdown.contains("<td><code class=\"inline-code\">Task task</code></td>"), "inline code in table cells");
        require(markdown.contains("<td><strong>Job</strong> を作る</td>"), "bold in table cells");
        require(markdown.contains("<p>次の表です。</p>") && markdown.contains("<p>終わりです。</p>"), "paragraphs between and after tables");
        require(!markdown.contains("|---|---|"), "table separator is not visible text");
        require(ChatHtmlConverter.convert("通常の文章").contains("<p>通常の文章</p>"), "plain markerless text accepted");

        String cells = ChatHtmlConverter.convert("Assistant:\n| 項目 | 値 |\n|---|---|\n"
                + "| `left | right` | a\\|b |\n"
                + "| path | `C:\\temp\\notes` |\n"
                + "| <script>alert(1)</script> | `</td><img src=x onerror=alert(1)>` |\n");
        require(cells.contains("<td><code class=\"inline-code\">left | right</code></td><td>a|b</td>"), "code and escaped pipes do not split cells");
        require(cells.contains("<code class=\"inline-code\">C:\\temp\\notes</code>"), "unrelated backslashes in code preserved");
        require(cells.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "table plain text escaped");
        require(cells.contains("&lt;/td&gt;&lt;img src=x onerror=alert(1)&gt;"), "table inline code escaped");
        require(!cells.contains("<script>") && !cells.contains("<img"), "table cells cannot inject HTML");
        String escapes = ChatHtmlConverter.convert("| A | B |\n|---|---|\n| `code | value |\n| regex | `\\\\d` |\n");
        require(escapes.contains("<td>`code</td><td>value</td>"), "unmatched backtick does not swallow table boundaries");
        require(escapes.contains("<code class=\"inline-code\">\\\\d</code>"), "literal backslash pair in inline code preserved");
        require(ChatHtmlConverter.convert("A | B\n---|---\nx | y\\|").contains("<td>x</td><td>y|</td>"), "escaped trailing pipe belongs to cell");

        String alignment = ChatHtmlConverter.convert("Left | Center | Right\n:--- | :---: | ---:\na | b | c");
        for (String position : new String[]{"left", "center", "right"}) {
            require(alignment.matches("(?s).*<th[^>]*class=\"align-" + position + "\"[^>]*>.*"), "header alignment " + position);
            require(alignment.matches("(?s).*<td[^>]*class=\"align-" + position + "\"[^>]*>.*"), "cell alignment " + position);
        }

        String ragged = ChatHtmlConverter.convert("| A | B | C |\n|---|---|---|\n| 1 | 2 |\n| 3 | 4 | 5 | extra |\n終了\n");
        require(ragged.contains("<tr><td>1</td><td>2</td><td></td></tr>"), "short table rows padded");
        require(ragged.contains("<tr><td>3</td><td>4</td><td>5</td></tr>"), "extra table cells ignored");
        require(ragged.contains("</table></div><p>終了</p>"), "plain paragraph terminates table without blank line");

        String literal = ChatHtmlConverter.convert("Assistant:\n```markdown\n| A | B |\n|---|---|\n| x | y |\n```\n\nA | B\n区切りではない");
        require(!literal.contains("<table>"), "table syntax inside code fence and ordinary pipes stay literal");
        require(literal.contains("<pre><code>| A | B |\n|---|---|\n| x | y |</code></pre>"), "fenced table content preserved");
        require(literal.contains("<p>A | B<br>\n区切りではない</p>"), "ordinary pipe prose preserved");
        String markerlessFence = ChatHtmlConverter.convert("```text\nUser:\nAssistant:\n```\n\nMarkdownの本文");
        require(markerlessFence.contains("<pre><code>User:\nAssistant:</code></pre>"), "role markers in markerless code fence remain literal");
        require(markerlessFence.contains("<p>Markdownの本文</p>"), "markerless text after fenced role markers preserved");
        require(!ChatHtmlConverter.convert("| A | B |\n|---|--|\n| x | y |").contains("<table>"), "invalid separator stays text");
        require(!ChatHtmlConverter.convert("| A | B |\n|---|---|---|\n| x | y |").contains("<table>"), "mismatched header separator stays text");

        for (String invalid : new String[]{"", " \n\t", "User:\n\nAssistant:"}) {
            boolean rejected = false;
            try { ChatHtmlConverter.convert(invalid); }
            catch (IllegalArgumentException e) { rejected = true; }
            require(rejected, "reject empty conversations");
        }
        System.out.println("Converter tests passed");
    }
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
    private static int occurrences(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
