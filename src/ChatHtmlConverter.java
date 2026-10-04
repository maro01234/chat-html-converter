import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * chat.txtの会話を、チャット風のchat.htmlへ変換します。
 *
 * 対応記法:
 *   # ～ ######        見出し
 *   **文字**          太字
 *   `文字`            インラインコード
 *   | 列 | 列 |       Markdown表
 *   ```               コードブロック
 */
public class ChatHtmlConverter {

    public static String convert(String source) {
        List<Message> messages = parseMessages(source.replaceFirst("^\\uFEFF", ""));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("変換する本文がありません。会話ログまたはMarkdownを入力してください。");
        }
        return createHtml(messages);
    }

    public static void main(String[] args) {
        Path projectFolder = Path.of(System.getProperty("user.dir"));
        Path inputFile = projectFolder.resolve("chat.txt");
        Path outputFile = projectFolder.resolve("chat-emoji.html");

        System.out.println("絵文字版: 2026-07-26-v2");
        System.out.println("現在の作業フォルダー: " + projectFolder.toAbsolutePath());

        try {
            if (!Files.exists(inputFile)) {
                System.err.println("chat.txtが見つかりません。");
                System.err.println("次の場所に作成してください: " + inputFile.toAbsolutePath());
                return;
            }

            String source = Files.readString(inputFile, StandardCharsets.UTF_8);
            String html = convert(source);
            Files.writeString(outputFile, html, StandardCharsets.UTF_8);

            System.out.println("HTMLを作成しました。");
            System.out.println(outputFile.toAbsolutePath());

        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        } catch (IOException e) {
            System.err.println("ファイル処理中にエラーが発生しました。");
            e.printStackTrace();
        }
    }

    private static List<Message> parseMessages(String source) {
        List<Message> messages = new ArrayList<>();
        String currentRole = null;
        StringBuilder currentText = new StringBuilder();
        boolean inCodeBlock = false;

        String[] lines = source.split("\\R", -1);

        for (String line : lines) {
            String trimmed = line.trim();

            if (!inCodeBlock && isUserMarker(trimmed)) {
                addMessage(messages, currentRole, currentText);
                currentRole = "user";
                currentText.setLength(0);

            } else if (!inCodeBlock && isAssistantMarker(trimmed)) {
                addMessage(messages, currentRole, currentText);
                currentRole = "assistant";
                currentText.setLength(0);

            } else if (currentRole != null) {
                if (!currentText.isEmpty()) {
                    currentText.append("\n");
                }
                currentText.append(line);
            }
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
            }
        }

        addMessage(messages, currentRole, currentText);
        if (currentRole == null && !source.isBlank()) {
            messages.add(new Message("assistant", source.strip()));
        }
        return messages;
    }

    private static boolean isUserMarker(String line) {
        return line.equalsIgnoreCase("User:")
                || line.equalsIgnoreCase("あなた:")
                || line.equalsIgnoreCase("自分:");
    }

    private static boolean isAssistantMarker(String line) {
        return line.equalsIgnoreCase("Assistant:")
                || line.equalsIgnoreCase("ChatGPT:")
                || line.equalsIgnoreCase("AI:");
    }

    private static void addMessage(
            List<Message> messages,
            String role,
            StringBuilder text) {

        if (role == null) {
            return;
        }

        String content = text.toString().strip();
        if (!content.isEmpty()) {
            messages.add(new Message(role, content));
        }
    }

    private static String createHtml(List<Message> messages) {
        StringBuilder messageHtml = new StringBuilder();

        for (Message message : messages) {
            boolean isUser = message.role().equals("user");
            String displayName = isUser ? "あなた" : "ChatGPT";
            String avatar = isUser ? "&#x1F466;" : "&#x1F916;";

            messageHtml
                    .append("<div class=\"message-row ")
                    .append(message.role())
                    .append("\">\n")
                    .append("  <div class=\"message-container\">\n")
                    .append("    <div class=\"avatar\"><span class=\"avatar-symbol\">")
                    .append(avatar)
                    .append("</span></div>\n")
                    .append("    <div class=\"message-area\">\n")
                    .append("      <div class=\"name\">")
                    .append(displayName)
                    .append("</div>\n")
                    .append("      <div class=\"bubble\">\n")
                    .append(formatMessage(message.content()))
                    .append("\n      </div>\n")
                    .append("    </div>\n")
                    .append("  </div>\n")
                    .append("</div>\n");
        }

        String template = """
                <!DOCTYPE html>
                <html lang="ja">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>ChatGPT 会話ログ・絵文字版</title>
                    <style>
                        * { box-sizing: border-box; }

                        html { scroll-behavior: smooth; }

                        body {
                            margin: 0;
                            background: #eef1f5;
                            color: #222;
                            font-family: -apple-system, BlinkMacSystemFont,
                                "Segoe UI", "Hiragino Sans", "Yu Gothic",
                                Meiryo, sans-serif;
                            line-height: 1.7;
                        }

                        header {
                            position: sticky;
                            top: 0;
                            z-index: 10;
                            padding: 15px 20px;
                            background: #202123;
                            color: white;
                            text-align: center;
                            font-size: 18px;
                            font-weight: bold;
                            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
                        }

                        main {
                            width: 100%;
                            max-width: 950px;
                            min-height: 100vh;
                            margin: 0 auto;
                            padding: 25px 18px 60px;
                        }

                        .message-row {
                            display: flex;
                            width: 100%;
                            margin-bottom: 26px;
                        }

                        .message-row.assistant { justify-content: flex-start; }
                        .message-row.user { justify-content: flex-end; }

                        .message-container {
                            display: flex;
                            align-items: flex-start;
                            max-width: 78%;
                            min-width: 0;
                            gap: 10px;
                        }

                        .message-row.user .message-container {
                            flex-direction: row-reverse;
                        }

                        .avatar {
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            flex-shrink: 0;
                            width: 42px;
                            height: 42px;
                            border-radius: 50%;
                            color: white;
                            font-size: 12px;
                            font-weight: bold;
                            line-height: 1;
                            box-shadow: 0 2px 5px rgba(0, 0, 0, 0.18);
                        }

                        .assistant .avatar {
                            background: #10a37f;
                        }

                        .user .avatar {
                            background: #3678d4;
                        }

                        /*
                         * 絵文字は親要素の文字サイズとは分離して指定します。
                         * 👦や🤖が小さく表示される問題を防ぎます。
                         */
                        .avatar-symbol {
                            display: block;
                            font-family:
                                "Apple Color Emoji",
                                "Segoe UI Emoji",
                                "Noto Color Emoji",
                                sans-serif;
                            font-size: 28px;
                            font-weight: normal;
                            line-height: 1;
                            transform: scale(1.08);
                            transform-origin: center;
                        }

                        .message-area { min-width: 0; }

                        .name {
                            margin: 0 8px 5px;
                            color: #666;
                            font-size: 12px;
                            font-weight: bold;
                        }

                        .user .name { text-align: right; }

                        .bubble {
                            position: relative;
                            padding: 13px 17px;
                            border-radius: 18px;
                            line-height: 1.7;
                            overflow-wrap: anywhere;
                            box-shadow: 0 2px 7px rgba(0, 0, 0, 0.10);
                        }

                        .assistant .bubble {
                            background: white;
                            color: #222;
                            border-top-left-radius: 5px;
                        }

                        .user .bubble {
                            background: #3678d4;
                            color: white;
                            border-top-right-radius: 5px;
                        }

                        .assistant .bubble::before {
                            content: "";
                            position: absolute;
                            top: 0;
                            left: -8px;
                            width: 0;
                            height: 0;
                            border-top: 12px solid white;
                            border-left: 10px solid transparent;
                        }

                        .user .bubble::before {
                            content: "";
                            position: absolute;
                            top: 0;
                            right: -8px;
                            width: 0;
                            height: 0;
                            border-top: 12px solid #3678d4;
                            border-right: 10px solid transparent;
                        }

                        .bubble p { margin: 0 0 12px; }
                        .bubble p:last-child { margin-bottom: 0; }

                        .table-scroll {
                            max-width: 100%;
                            overflow-x: auto;
                            margin: 16px 0;
                            border: 1px solid #dce2e8;
                            border-radius: 8px;
                            background: white;
                            color: #222;
                        }
                        .table-scroll table {
                            width: 100%;
                            border-collapse: collapse;
                            font-size: 0.95em;
                        }
                        .table-scroll th, .table-scroll td {
                            padding: 10px 14px;
                            border-bottom: 1px solid #dce2e8;
                            text-align: left;
                            vertical-align: top;
                            min-width: 100px;
                        }
                        .table-scroll th { background: #f3f5f7; font-weight: 700; }
                        .table-scroll tbody tr:last-child td { border-bottom: 0; }
                        .table-scroll .align-left { text-align: left; }
                        .table-scroll .align-center { text-align: center; }
                        .table-scroll .align-right { text-align: right; }
                        .table-scroll .inline-code {
                            white-space: normal;
                            overflow-wrap: anywhere;
                        }
                        .user .bubble .table-scroll .inline-code { background: #eef0f3; color: #c7254e; }
                        .user .bubble .table-scroll strong { color: #111; text-decoration: none; }

                        .bubble h1 {
                            margin: 22px 0 12px;
                            padding-bottom: 7px;
                            font-size: 24px;
                            line-height: 1.4;
                            border-bottom: 2px solid rgba(128, 128, 128, 0.35);
                        }

                        .bubble h2 {
                            margin: 20px 0 10px;
                            padding-bottom: 5px;
                            font-size: 20px;
                            line-height: 1.4;
                            border-bottom: 1px solid rgba(128, 128, 128, 0.30);
                        }

                        .bubble h3 {
                            margin: 18px 0 8px;
                            font-size: 17px;
                            line-height: 1.4;
                        }

                        .bubble h4 {
                            margin: 16px 0 7px;
                            font-size: 16px;
                            line-height: 1.4;
                        }

                        .bubble h5 {
                            margin: 14px 0 6px;
                            font-size: 15px;
                            line-height: 1.4;
                        }

                        .bubble h6 {
                            margin: 12px 0 5px;
                            color: #666;
                            font-size: 14px;
                            line-height: 1.4;
                        }

                        .bubble h1:first-child,
                        .bubble h2:first-child,
                        .bubble h3:first-child,
                        .bubble h4:first-child,
                        .bubble h5:first-child,
                        .bubble h6:first-child {
                            margin-top: 0;
                        }

                        .user .bubble h1,
                        .user .bubble h2 {
                            border-bottom-color: rgba(255, 255, 255, 0.40);
                        }

                        .user .bubble h6 {
                            color: rgba(255, 255, 255, 0.85);
                        }

                        .bubble strong { font-weight: 700; }
                        .assistant .bubble strong { color: #111; }

                        .chat-image {
                            display: block;
                            max-width: 100%;
                            height: auto;
                            border-radius: 8px;
                        }

                        .image-placeholder {
                            display: block;
                            padding: 12px;
                            border: 1px dashed currentColor;
                            border-radius: 8px;
                            overflow-wrap: anywhere;
                        }

                        .user .bubble strong {
                            color: #fff;
                            text-decoration: underline;
                            text-decoration-thickness: 2px;
                            text-underline-offset: 3px;
                        }

                        .inline-code {
                            display: inline;
                            padding: 2px 6px;
                            border-radius: 5px;
                            background: rgba(100, 100, 100, 0.14);
                            color: #c7254e;
                            font-family: "SFMono-Regular", Consolas,
                                "Liberation Mono", Menlo, monospace;
                            font-size: 0.92em;
                            white-space: nowrap;
                        }

                        .user .bubble .inline-code {
                            background: rgba(255, 255, 255, 0.22);
                            color: white;
                        }

                        pre {
                            max-width: 100%;
                            margin: 12px 0 5px;
                            padding: 14px;
                            overflow-x: auto;
                            border-radius: 9px;
                            background: #1e1e1e;
                            color: #f1f1f1;
                            line-height: 1.5;
                            white-space: pre;
                            box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.05);
                        }

                        pre code {
                            padding: 0;
                            background: transparent;
                            color: inherit;
                            white-space: pre;
                        }

                        code {
                            font-family: "SFMono-Regular", Consolas,
                                "Liberation Mono", Menlo, monospace;
                            font-size: 13px;
                        }

                        @media (max-width: 650px) {
                            main { padding: 18px 10px 50px; }
                            .message-container { max-width: 92%; }
                            .avatar {
                                width: 34px;
                                height: 34px;
                                font-size: 10px;
                            }
                            .avatar-symbol {
                                font-size: 23px;
                                transform: scale(1.08);
                            }
                            .bubble {
                                padding: 11px 14px;
                                font-size: 15px;
                            }
                            .bubble h1 { font-size: 21px; }
                            .bubble h2 { font-size: 18px; }
                        }

                        @media print {
                            header {
                                position: static;
                                box-shadow: none;
                            }
                            body { background: white; }
                            main { max-width: none; }
                            .message-row { break-inside: avoid; }
                        }
                    </style>
                </head>
                <body>
                    <header>ChatGPT 会話ログ</header>
                    <main>
                        {{MESSAGES}}
                    </main>
                </body>
                </html>
                """;

        return template.replace("{{MESSAGES}}", messageHtml.toString());
    }

    private static String formatMessage(String text) {
        StringBuilder html = new StringBuilder();
        StringBuilder normalText = new StringBuilder();
        StringBuilder codeText = new StringBuilder();
        boolean inCodeBlock = false;

        String[] lines = text.split("\\R", -1);

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (!inCodeBlock) {
                    appendNormalText(html, normalText);
                    inCodeBlock = true;
                    codeText.setLength(0);
                } else {
                    appendCodeBlock(html, codeText);
                    inCodeBlock = false;
                }
                continue;
            }

            if (inCodeBlock) {
                if (!codeText.isEmpty()) {
                    codeText.append("\n");
                }
                codeText.append(line);
                continue;
            }

            ImageAttachment image = parseImageAttachment(trimmed);
            if (image != null) {
                appendNormalText(html, normalText);
                html.append("<p class=\"image-block\"><span class=\"image-placeholder\" data-attachment-id=\"")
                        .append(image.id())
                        .append("\">画像: ")
                        .append(escapeHtml(image.alt()))
                        .append("（画像ファイルを選択してください）</span></p>");
                continue;
            }

            int headingLevel = getHeadingLevel(trimmed);

            if (headingLevel > 0) {
                appendNormalText(html, normalText);
                String headingText = trimmed.substring(headingLevel + 1);

                html.append("<h")
                        .append(headingLevel)
                        .append(">")
                        .append(formatInlineMarkdown(headingText))
                        .append("</h")
                        .append(headingLevel)
                        .append(">");

            } else {
                if (!normalText.isEmpty()) {
                    normalText.append("\n");
                }
                normalText.append(line);
            }
        }

        if (inCodeBlock) {
            appendCodeBlock(html, codeText);
        }

        appendNormalText(html, normalText);
        return html.toString();
    }

    // Only local attachment markers on their own line are recognized. Images are
    // embedded by the browser after conversion; the server never fetches a URL.
    private static ImageAttachment parseImageAttachment(String line) {
        if (!line.startsWith("![") || !line.endsWith(")")) return null;
        StringBuilder alt = new StringBuilder();
        int index = 2;
        for (; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '\\' && index + 1 < line.length()) {
                char next = line.charAt(index + 1);
                if (next == '\\' || next == '[' || next == ']') {
                    alt.append(next);
                    index++;
                    continue;
                }
            }
            if (current == ']') break;
            alt.append(current);
        }
        String prefix = "](attachment:";
        if (!line.startsWith(prefix, index)) return null;
        String id = line.substring(index + prefix.length(), line.length() - 1);
        if (id.isEmpty() || id.length() > 80) return null;
        for (int i = 0; i < id.length(); i++) {
            char current = id.charAt(i);
            if (!(current >= 'a' && current <= 'z') && !(current >= 'A' && current <= 'Z')
                    && !(current >= '0' && current <= '9') && current != '-') return null;
        }
        return new ImageAttachment(id, alt.toString());
    }

    private static int getHeadingLevel(String line) {
        for (int level = 6; level >= 1; level--) {
            String prefix = "#".repeat(level) + " ";
            if (line.startsWith(prefix)) {
                return level;
            }
        }
        return 0;
    }

    private static void appendNormalText(
            StringBuilder html,
            StringBuilder normalText) {

        if (normalText.isEmpty()) {
            return;
        }

        String[] lines = normalText.toString().split("\\n", -1);
        StringBuilder paragraph = new StringBuilder();

        for (int index = 0; index < lines.length;) {
            if (index + 1 < lines.length && isMarkdownTableStart(lines[index], lines[index + 1])) {
                appendParagraph(html, paragraph);
                index = appendMarkdownTable(html, lines, index);
                continue;
            }

            if (lines[index].isBlank()) {
                appendParagraph(html, paragraph);
            } else {
                if (!paragraph.isEmpty()) {
                    paragraph.append("\n");
                }
                paragraph.append(lines[index]);
            }
            index++;
        }

        appendParagraph(html, paragraph);

        normalText.setLength(0);
    }

    private static void appendParagraph(StringBuilder html, StringBuilder paragraph) {
        String trimmed = paragraph.toString().strip();
        if (trimmed.isEmpty()) {
            paragraph.setLength(0);
            return;
        }

        html.append("<p>");
        String[] lines = trimmed.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            html.append(formatInlineMarkdown(lines[index]));
            if (index < lines.length - 1) {
                html.append("<br>\n");
            }
        }
        html.append("</p>");
        paragraph.setLength(0);
    }

    private static boolean isMarkdownTableStart(String headerLine, String separatorLine) {
        List<String> header = parseTableRow(headerLine);
        List<String> separator = parseTableRow(separatorLine);
        if (header == null || separator == null || header.size() != separator.size()) {
            return false;
        }
        return separator.stream().allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private static int appendMarkdownTable(StringBuilder html, String[] lines, int start) {
        List<String> header = parseTableRow(lines[start]);
        List<String> separator = parseTableRow(lines[start + 1]);
        html.append("<div class=\"table-scroll\"><table><thead><tr>");
        for (int column = 0; column < header.size(); column++) {
            appendTableCell(html, "th", header.get(column), separator.get(column));
        }
        html.append("</tr></thead><tbody>");
        int index = start + 2;
        while (index < lines.length && !lines[index].isBlank()) {
            List<String> row = parseTableRow(lines[index]);
            if (row == null || (index + 1 < lines.length
                    && isMarkdownTableStart(lines[index], lines[index + 1]))) {
                break;
            }
            html.append("<tr>");
            for (int column = 0; column < header.size(); column++) {
                appendTableCell(html, "td", column < row.size() ? row.get(column) : "", separator.get(column));
            }
            html.append("</tr>");
            index++;
        }
        html.append("</tbody></table></div>");
        return index;
    }

    private static void appendTableCell(StringBuilder html, String tag, String text, String separator) {
        html.append('<').append(tag);
        if (tag.equals("th")) html.append(" scope=\"col\"");
        String alignment = separator.endsWith(":")
                ? (separator.startsWith(":") ? "center" : "right")
                : (separator.startsWith(":") ? "left" : "");
        if (!alignment.isEmpty()) html.append(" class=\"align-").append(alignment).append('"');
        html.append('>').append(formatInlineMarkdown(text)).append("</").append(tag).append('>');
    }

    private static List<String> parseTableRow(String line) {
        String value = line.strip();
        // A backtick is literal unless a closing run of equal length follows it.
        Map<Integer, Integer> lastTickRun = new HashMap<>();
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '`') continue;
            int end = index + 1;
            while (end < value.length() && value.charAt(end) == '`') end++;
            lastTickRun.put(end - index, index);
            index = end - 1;
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        int codeTicks = 0;
        boolean hasSeparator = false;
        boolean leadingSeparator = false;
        boolean trailingSeparator = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\\' && index + 1 < value.length()
                    && (value.charAt(index + 1) == '|' || value.charAt(index + 1) == '\\')) {
                if (codeTicks > 0 && value.charAt(index + 1) == '\\') cell.append('\\');
                cell.append(value.charAt(++index));
            } else if (current == '`') {
                int end = index + 1;
                while (end < value.length() && value.charAt(end) == '`') end++;
                int ticks = end - index;
                if (codeTicks == 0 && lastTickRun.get(ticks) > index) codeTicks = ticks;
                else if (codeTicks == ticks) codeTicks = 0;
                cell.append(value, index, end);
                index = end - 1;
            } else if (current == '|' && codeTicks == 0) {
                hasSeparator = true;
                if (index == 0) leadingSeparator = true;
                if (index == value.length() - 1) trailingSeparator = true;
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(current);
            }
        }
        if (!hasSeparator) return null;
        cells.add(cell.toString().strip());
        if (trailingSeparator) cells.remove(cells.size() - 1);
        if (leadingSeparator) cells.remove(0);
        return cells.isEmpty() ? null : cells;
    }

    private static void appendCodeBlock(
            StringBuilder html,
            StringBuilder codeText) {

        html.append("<pre><code>")
                .append(escapeHtml(codeText.toString()))
                .append("</code></pre>");

        codeText.setLength(0);
    }

    private static String formatInlineMarkdown(String text) {
        StringBuilder result = new StringBuilder();
        int position = 0;

        while (position < text.length()) {
            int strongOpening = text.indexOf("**", position);
            int codeOpening = text.indexOf("`", position);

            if (strongOpening < 0 && codeOpening < 0) {
                result.append(escapeHtml(text.substring(position)));
                break;
            }

            int nextOpening;
            boolean strongFirst;

            if (strongOpening < 0) {
                nextOpening = codeOpening;
                strongFirst = false;
            } else if (codeOpening < 0) {
                nextOpening = strongOpening;
                strongFirst = true;
            } else if (strongOpening <= codeOpening) {
                nextOpening = strongOpening;
                strongFirst = true;
            } else {
                nextOpening = codeOpening;
                strongFirst = false;
            }

            result.append(escapeHtml(text.substring(position, nextOpening)));

            if (strongFirst) {
                int closing = text.indexOf("**", nextOpening + 2);

                if (closing < 0) {
                    result.append(escapeHtml(text.substring(nextOpening)));
                    break;
                }

                String strongText = text.substring(nextOpening + 2, closing);

                if (strongText.isEmpty()) {
                    result.append("**");
                    position = nextOpening + 2;
                    continue;
                }

                result.append("<strong>")
                        .append(formatInlineCodeOnly(strongText))
                        .append("</strong>");

                position = closing + 2;

            } else {
                int closing = text.indexOf("`", nextOpening + 1);

                if (closing < 0) {
                    result.append(escapeHtml(text.substring(nextOpening)));
                    break;
                }

                String inlineCode = text.substring(nextOpening + 1, closing);

                if (inlineCode.isEmpty()) {
                    result.append("`");
                    position = nextOpening + 1;
                    continue;
                }

                result.append("<code class=\"inline-code\">")
                        .append(escapeHtml(inlineCode))
                        .append("</code>");

                position = closing + 1;
            }
        }

        return result.toString();
    }

    private static String formatInlineCodeOnly(String text) {
        StringBuilder result = new StringBuilder();
        int position = 0;

        while (position < text.length()) {
            int opening = text.indexOf("`", position);

            if (opening < 0) {
                result.append(escapeHtml(text.substring(position)));
                break;
            }

            result.append(escapeHtml(text.substring(position, opening)));
            int closing = text.indexOf("`", opening + 1);

            if (closing < 0) {
                result.append(escapeHtml(text.substring(opening)));
                break;
            }

            String inlineCode = text.substring(opening + 1, closing);

            if (inlineCode.isEmpty()) {
                result.append("`");
                position = opening + 1;
                continue;
            }

            result.append("<code class=\"inline-code\">")
                    .append(escapeHtml(inlineCode))
                    .append("</code>");

            position = closing + 1;
        }

        return result.toString();
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record Message(String role, String content) {
    }

    private record ImageAttachment(String id, String alt) {
    }
}
