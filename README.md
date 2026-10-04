# Chat HTML Converter

ChatGPTのテキストログを、絵文字付きのチャット風HTMLへ変換する日本語ウェブアプリです。元の `ChatHtmlConverter.java` の表示とMarkdown変換を引き継いでいます。

## できること

- ログの貼り付け、UTF-8の `.txt` / `.md` ファイルの読み込み（最大2 MiB）
- 吹き出し表示のプレビュー、単独で開ける `chat-emoji.html` のダウンロード
- 見出し、太字、Markdown表、インラインコード、コードブロックの変換
- 話者名のないChatGPT・Codexの回答やMarkdownファイルも、そのまま変換
- PNG・JPEG・GIF・WebP画像を本文のカーソル位置に挿入（画像の貼り付けにも対応）
- 画像をHTMLに埋め込んで保存し、オフラインでも表示
- スマートフォンからの利用

入力は変換時にサーバーへ送信します。アプリは会話本文をファイル・データベース・アクセスログに保存しません。ブラウザのローカルストレージも使いません。ページの再読み込みで入力は消えます。HTML内のユーザー入力はエスケープし、プレビューはsandbox付きiframeで表示します。

## 画像を挿入

本文の挿入したい位置をクリックし、「画像を挿入」から画像を選びます。画像をクリップボードから本文に貼り付けることもできます。挿入位置には `![画像名](attachment:画像ID)` という行が追加されます。この行を移動すると画像の位置も変わります。画像一覧から同じ画像の再挿入・削除ができます。

PNG・JPEG・GIF・WebPに対応し、1枚10 MiB、合計20 MiB、最大20枚までです。画像本体はサーバーへ送信せず、ブラウザで変換後のHTMLに埋め込みます（本文に含まれる画像名とIDは送信されます）。画像を含むHTMLは単独で保存・共有できます。ページを閉じたり再読み込みしたりする前にHTMLをダウンロードしてください。Markdownだけを保存しても画像本体は含まれません。外部画像URLやSVGは画像として読み込みません。

## 入力形式

Markdownはそのまま入力できます。話者名がない場合は、全体を1つのアシスタントの回答として表示します。会話を話者ごとに分ける場合は、話者名を単独の行に置き、次の行から本文を書きます。

````text
User:
こんにちは！

Assistant:
# こんにちは
**太字** と `コード` に対応しています。

```java
System.out.println("Hello!");
```
````

ユーザー側は `User:` / `あなた:` / `自分:`、アシスタント側は `Assistant:` / `ChatGPT:` / `AI:` に対応します。英字の大小は区別しません。区切りは半角コロンです。話者マーカーがある場合、その前の文章は無視されます。コードブロック内の話者マーカーや表は本文として保持します。JSON・ZIPのエクスポートや、一般的なMarkdownのリストの整形には対応していません。

表は見出し行の直後に `|---|---|` のような区切り行を置きます。セル内の太字・インラインコード、`\|` によるパイプ文字、区切り行の `:---` / `:---:` / `---:` による列揃えに対応します。幅の広い表は横スクロールでき、ダウンロードしたHTMLにも表のスタイルが含まれます。

## ローカルで起動

Java 21以降が必要です。追加ライブラリ・APIキー・データベースは不要です。

```sh
mkdir -p out
javac --release 21 --add-modules jdk.httpserver -encoding UTF-8 -d out src/*.java
java --add-modules jdk.httpserver -cp out WebServer
```

プロジェクトのルートで実行し、ブラウザで http://localhost:10000 を開きます。停止は Ctrl+C。ポートは環境変数 `PORT` で変更できます。

## Renderで公開

JavaはDockerでデプロイします。`Dockerfile` と `render.yaml` を用意してあります。

1. GitHubにこのプロジェクト用のリポジトリを作成し、プロジェクトのファイルをpushします（`out/` や実際の会話ログは含めません）。
2. [Render Dashboard](https://dashboard.render.com/) で **New → Blueprint** を選び、GitHubリポジトリを接続します。
3. ルートの `render.yaml` が検出されたことと、サービス名・Freeプランを確認してデプロイします。
4. デプロイ完了後、発行された `https://…onrender.com` を開きます。

手動で **New → Web Service** を選ぶ場合は、リポジトリを指定し、Languageを **Docker**、Dockerfile Pathを `./Dockerfile`、Health Check Pathを `/healthz`、プランを **Free** にしてください。Docker Commandは空欄で構いません。ルートディレクトリはこのプロジェクトのルートです。

サーバーはRenderの `PORT` を使い、`0.0.0.0` で待ち受けます。無料サービスはアイドル後の起動に時間がかかる場合があります。ログイン機能はないため、公開URLへアクセスできる人は誰でも変換機能を利用できます。

参照: [RenderのDocker対応](https://render.com/docs/docker)、[Web Services](https://render.com/docs/web-services)、[Blueprint仕様](https://render.com/docs/blueprint-spec)、[Freeプラン](https://render.com/docs/free)

## 動作確認

```sh
javac --release 21 --add-modules jdk.httpserver -encoding UTF-8 -d out src/*.java tests/ConverterTest.java
java -cp out ConverterTest
node tests/image_frontend_test.js
```

サーバーを起動した別のターミナルで:

```sh
python3 tests/http_test.py
```

Dockerでもビルド時に変換テストを実行します。

```sh
docker build -t chat-html-converter .
docker run --rm -p 10000:10000 chat-html-converter
```

元のコマンドライン版も残してあります。ルートに `chat.txt` を置いて `java -cp out ChatHtmlConverter` を実行すると、`chat-emoji.html` を出力します。

## オンラインデモ
[https://java-streamlit-demo.onrender.com/](https://chat-html-converter.onrender.com/)
