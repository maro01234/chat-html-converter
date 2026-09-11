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
        for (String invalid : new String[]{"", "no markers", "User:\n\nAssistant:"}) {
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
}
