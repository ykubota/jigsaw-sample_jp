# Project Jigsaw Demonstration

## 元の構成(01_元のコード)

サーバ`com.example.server`のAPIをクライアント`net.example.client`や`org.example.music`が呼び出す簡単なアプリ。

* `client`は公開APIを呼び、`music`は音楽APIを呼ぶ
* `server`は各APIの実処理を内部APIで実装している
  * 内部APIは完全に隠蔽したいがパッケージ構成の都合上`public`なので誰でも呼べてしまう
  * 音楽APIは音楽関係のパッケージに限定したいが、Java8まではそんな仕組みはなかった

```
src
|-- com
|   `-- example
|       `-- server
|           |-- internal
|           |   `-- 内部API.java
|           |-- music
|           |   `-- 音楽API.java
|           `-- open
|               `-- 公開API.java
|-- net
|   `-- example
|       `-- client
|           |-- 公開APIを呼び出す.java
|           `-- 内部APIを呼び出す.java
`-- org
    `-- example
        `-- music
            `-- 音楽をかける.java
```

### ビルドと実行

* ビルド

```
% javac -d build -cp . $(find src -name "*java")
```

* 実行

```
% java -cp build net.example.client.公開APIを呼び出す
公開API経由で内部APIを実行
% java -cp build org.example.music.音楽をかける
♪～
# 本来は呼ばせたくない
% java -cp build net.example.client.内部APIを呼び出す
内部APIを実行
```

## ソースコード構成をモジュール化(02_モジュール化)

各パッケージごとにモジュール化する。なお、内部APIを呼ぶ処理は一端削除する。

```
src/
|-- server
|   |-- com
|   |   `-- example
|   |       `-- server
|   |           |-- internal
|   |           |   `-- 内部API.java
|   |           |-- music
|   |           |   `-- 音楽API.java
|   |           `-- open
|   |               `-- 公開API.java
|   `-- module-info.java
|-- client
|   |-- net
|   |   `-- example
|   |       `-- client
|   |           `-- 公開APIを呼び出す.java
|   `-- module-info.java
`-- music
    |-- org
    |   `-- example
    |       `-- music
    |           `-- 音楽をかける.java
    `-- module-info.java
```

各モジュールごとにディレクトリを作り、直下に`module-info.java`を作る。コードは各モジュールディレクトリ配下にそのままディレクトリごとコピーする。

今回は見易さを優先してモジュール名を簡潔にしたが、パッケージと同様に重複しない名前を付けること(パッケージ名をそのままモジュール名にするのが無難)。

### ビルド

* 方法１：モジュール化されたコードが配置されているディレクトリを指定してモジュールごとにビルドする

```
% javac -d mods --module-source-path src -m server
% javac -d mods --module-source-path src -m client
% javac -d mods --module-source-path src -m music
```

* 方法２：旧来のようにJavaファイルを一つずつ指定する

```
$ cp -r 依存しているモジュール mods/*
% javac -p mods -d mods/server \
      src/server/module-info.java \
      src/server/com/example/server/open/公開API.java \
      src/server/com/example/server/internal/内部API.java \
      src/server/com/example/server/music/音楽API.java
% javac -p mods -d mods/client $(find src/client/ -name "*.java")
% javac -p mods -d mods/music $(find src/music/ -name "*.java")
```

### 実行

```
% java -p mods -m client/net.example.client.公開APIを呼び出す
公開API経由で内部APIを実行
% java -p mods -m music/org.example.music.音楽をかける
♪～
```

### ライブラリ化

JARファイルを作成する。ちなみにモジュール化されているJARファイルのことをModular JARと呼ぶらしい。

```
% mkdir mlibs
% jar --create --file mlibs/server.jar \
      --module-version 1.0 -C mods/server .
% jar --create --file mlibs/client.jar \
      --module-version 1.0 -C mods/client .
% jar --create --file mlibs/music.jar \
      --module-version 1.0 -C mods/music .
% tree mlibs
mlibs
|-- client.jar
|-- music.jar
`-- server.jar
% java -p mlibs -m music/org.example.music.音楽をかける
♪～
# メインクラスを指定すると起動時に指定が不要になる
% jar --create --file mlibs/music.jar \
      --main-class org.example.music.音楽をかける \
      --module-version 1.0 -C mods/music .
% java -p mlibs -m music
♪～
```

## JLink でスリム化(03_jlink)

標準ライブラリもモジュール化されたため、`jlink`を用いることでそのアプリに必要なモジュールだけで構築されたランタイムイメージを作れる。

ここでは`music`アプリ用のランタイムイメージを作成してみよう。

```
#ビルドする
% javac -d mods --module-source-path src -m server
% javac -d mods --module-source-path src -m music
#カスタムランタイムイメージを作る
% jlink --compress=2 -p ${JAVA_HOME}/jmods:mods --add-modules server,music --output dist/music_dir --launcher music_bin=music/org.example.music.Main
#実行する
% ./dist/music_dir/bin/music_bin
♪～
```

`dist`以下をポートすることでJavaがインストールしていない環境でも実行することができる。サイズもJava全てをインストールするより軽い。

```
% du -h dist | tail -n 1
33M     dist
$ du -h ${JAVA_HOME} | tail -n 1
544M    /home/ykubota/jdk-9/
```

コンテナで捗りますね。

## エラー集(04_エラー集)

### 公開されてないパッケージのAPIを呼び出した(01_内部APIの呼び出し)

`server`は内部APIを公開しないように設定していますが、当初のように`client`で内部APIを呼び出していた状態でビルドしてみるとどうなるでしょうか。

```
% find 01_内部APIの呼び出し/src/client -name "内部API*"
01_内部APIの呼び出し/src/client/net/example/client/内部APIを呼び出す.java
% javac -d mods --module-source-path 01_内部APIの呼び出し/src -m server
% javac -p mods -d mods/client $(find 01_内部APIの呼び出し/src/client -name "*java")
01_内部APIの呼び出し/src/client/net/example/client/内部APIを呼び出す.java:3: エラー: パッケージcom.example.server.internalは表示不可です
import com.example.server.internal.内部API;
                         ^
  (パッケージcom.example.server.internalはモジュールserverで宣言されていますが、エクスポートされていません)
エラー1個
```

無事怒られました

### 依存しているモジュールを定義していなかった(02_モジュール依存忘れ)

`music`がもし音楽APIを持つモジュール`server`への依存を定義しておらず、読み込んでいなかった場合はどうなるでしょうか。

```
% cat 02_モジュール依存忘れ/src/music/module-info.java
module music {
  //依存モジュールの読み込み忘れ
  //requires server;
}
% javac -p mods -d mods/music $(find 02_モジュール依存忘れ/src/music -name "*java")
02_モジュール依存忘れ/src/music/org/example/music/音楽をかける.java:3: エラー: パッケージcom.example.server.musicは表示不可です
import com.example.server.music.音楽API;
                         ^
  (パッケージcom.example.server.musicはモジュールserverで宣言されていますが、モジュールmusicに読み込まれていません)
エラー1個
```

親切ですね
