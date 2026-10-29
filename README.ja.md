# WanderTrace · 游迹

[English](README.md) · [简体中文](README.zh-CN.md) · **日本語**

ROOT と LSPosed を利用して、Android の位置情報、ルート移動、歩数をシステム全体でシミュレーションするモジュールです。地図上で位置を選び、ルートを作成し、移動速度とケイデンス（歩数/分）を同じアプリから設定できます。

| 項目 | 内容 |
| --- | --- |
| アプリ名 | 游迹 · WanderTrace |
| パッケージ ID | `com.wandertrace` |
| ネイティブアーキテクチャ | `arm64-v8a` |
| インストール可能な最低バージョン | Android 8.1 / API 27 |
| フレームワーク API | libxposed API 102 |
| プロジェクトのライセンス | GPL-3.0-only |

## 主な機能

- **固定位置のシミュレーション**：地図をドラッグして中央の照準を目的地に合わせ、その位置で開始します。緯度・経度の手入力は不要です。
- **ルート移動**：地図中央の位置を順番に追加して複数の地点をつなぎます。下書きの自動保存と、名前を付けた複数ルートの保存・読み込み・削除に対応します。
- **移動プリセット**：徒歩、ジョギング、ランニング、自転車、自動車、カスタムを選択できます。選択に応じて推奨速度が反映され、表示単位は **m/s** です。
- **滑らかな速度変化**：基準速度に対して **0–50%** のランダム変動幅を指定し、目標速度間を線形に変化させます。
- **ケイデンス設定**：無効、速度に応じた自動調整、任意の歩数/分から選択できます。変化率に制限を設け、徐々に変化させます。
- **GPS の揺らぎ**：半径 **1–3 メートル**の揺らぎを設定できます。
- **地図操作**：道路・衛星レイヤー、場所の検索、連続的なピンチズーム、中央照準での地点選択に対応します。Leaflet はアプリに同梱されています。
- **操作の集約**：移動方法、速度、ケイデンス、ループをルート開始ダイアログで設定します。実行中は開始ボタンが赤い停止ボタンに変わります。
- **自動確認**：アプリを開いたときやフォアグラウンドに戻したときに、ROOT 権限と LSPosed の状態を確認します。

## 動作要件

- ROOT を取得した **arm64** Android 端末と、WanderTrace 自体への ROOT 許可。
- **libxposed API 102** を提供する LSPosed または互換フレームワーク。
- モジュールを有効にし、推奨スコープの **System Framework（システムフレームワーク）** を選択すること。宣言上の名前は `system` です。
- 地図タイルとオンライン検索にアクセスするためのネットワーク接続。

Android 8.1 は APK をインストールできる最低バージョンであり、すべての ROM での動作保証ではありません。システムの位置情報インターフェースとネイティブ SensorService の ABI が実装に適合している必要があります。通常のアプリをモジュールのスコープに追加する必要はありませんが、各アプリの位置情報や身体活動などの Android 権限は引き続き適用されます。

## セットアップと使い方

1. 後述の手順で APK をビルドし、インストールします。
2. ROOT 管理アプリで **游迹 · WanderTrace** に ROOT アクセスを許可します。
3. LSPosed でモジュールを有効にし、推奨の **System Framework** スコープを選択します。シミュレーションには、対象プロセスへのモジュールの読み込みが必要です。
4. 初回起動時に地図プロバイダーを選び、設定を完了します。高徳地図では **Web サービス Key を 1 つ**入力します。
5. 下部の **固定位置（单点模拟）**、**ルート（路线模拟）**、**設定（设置）** のタブを使用します。

固定位置では地図を動かして中央照準を目的地に合わせ、**開始（开始模拟）** を押します。ルートでは各位置で **地点を追加（添加选点）** を押し、2 地点以上追加してから開始します。表示されたダイアログで移動パラメーターを設定してください。地図をタップするだけでは地点は追加されず、保存済みルートの編集・読み込みでも自動的にシミュレーションは開始されません。

**設定**で地図プロバイダー、レイヤー、揺らぎなどの位置情報オプションを変更できます。赤い **停止（停止模拟）** ボタンで出力を停止します。ループでは終点から始点に戻るため、連続して移動させたい場合は閉じたルートを作成してください。

異なるパッケージ ID の旧ビルドを、このパッケージで上書きすることはできません。旧アプリのシミュレーションを停止し、旧モジュールを無効にしてから、`com.wandertrace` の権限とモジュールを設定してください。両方のモジュールを同時に有効にしないでください。

## Key を 1 つだけ使用する地図設定

初回設定と設定画面には、選択中のプロバイダー用の認証情報欄が 1 つだけ表示されます。**高徳地図は Web サービス Key のみで利用でき、Android SDK Key や証明書の SHA-1 設定は不要です。**

| プロバイダー | 認証情報 | 地図レイヤー | 場所の検索 |
| --- | --- | --- | --- |
| AMap / 高徳地図 | 高徳 Web サービス Key | 高徳の公開道路・衛星タイルと注記 | 高徳のジオコーディング |
| Tencent Maps / 騰訊地図 | 騰訊 WebService Key | 騰訊の公開道路・衛星タイルと注記 | 騰訊のジオコーディング |
| 百度検索・高徳ベースマップ | 百度サーバー用 AK | 高徳の道路・衛星タイル | 百度のジオコーディング、GCJ-02 を取得 |
| 天地図 | 天地図サーバー用 `tk` | 同じ `tk` を使用する道路・衛星 WMTS レイヤーと注記 | 天地図のジオコーディング |
| OpenStreetMap | ベースマップは Key 不要 | 道路地図 | 高徳 Web サービス Key の追加で検索を有効化 |

異なるプロバイダー間で Key を共用することはできません。利用するプロバイダーだけを設定でき、切り替え時も認証情報は個別に保存されます。オンライン検索にはジオコーディングの利用権限と残り利用枠が必要です。追加の署名用秘密情報 `sig`、`sn`、`SK` を要求する設定には対応していません。

高徳の Key は検索に使用し、公開ベースマップのリクエストには含めず、Android SDK も初期化しません。公開タイルのエンドポイントは Key の有効性とは無関係に変更される可能性があります。高徳のタイルはズーム 18 まで取得し、それを拡大してズーム 20 まで表示します。さらに拡大しても元画像の詳細は増えません。

ルートは GCJ-02 で保存し、OSM や天地図など WGS-84 のベースマップへ描画するときに座標を変換します。詳細は[地図設定の説明](docs/WEB_SERVICE_KEYS.md)（中国語）を参照してください。

## システム連携とホットリロード

位置情報は `system_server` のプロバイダー配信層で処理します。歩数はシステムのネイティブセンサーイベント経路で処理し、非ウェイクアップ型の標準 **STEP_COUNTER / STEP_DETECTOR** を対象とします。Android のテスト位置情報プロバイダーやセンサーデータ注入モードは使用せず、GNSS やセンサーのファームウェアも書き換えません。メーカー独自のヘルスケアサービス、独自センサー、ほかの ROM 実装は個別の検証が必要です。

ルートとシミュレーションのパラメーターは実行中に読み取ります。同一プロセス内で Java モジュールのコードを更新するには、フレームワークの対応と、互換性のある API 102 エントリーポイントの読み込みが必要です。ネイティブブリッジはプロセスの生存期間中常駐し、ブリッジ ABI の非互換やネイティブライブラリのハッシュ変更があるとホットリロードを拒否します。初回インストール、legacy エントリーポイントからの移行、パッケージ ID の変更、ネイティブライブラリの置き換えは、このホットリロードの保証範囲外です。

`system_server`、Zygote、システム UI、ほかのアプリを強制終了する代替処理はありません。ライフサイクルの詳細は[ホットリロードの設計](docs/HOT_RELOAD.md)（中国語）を参照してください。

## ソースからのビルド

### ツールチェーン

- JDK **17 または 21**。
- Android SDK Platform **37.0**、Build Tools **36.0.0**。
- Android NDK **r27d**、CMake **3.18 以降**、Ninja。CMake と Ninja を `PATH` に追加してください。
- 同梱の Gradle Wrapper **9.3.1**、Android Gradle Plugin **9.1.1**。

ローカル専用の `local.properties` に `sdk.dir` を設定するか、Android SDK の環境変数を設定してください。以下のコマンドはリポジトリのルートで実行します。**先にネイティブライブラリをビルドしてください。** 生成された `.so` はリポジトリに含まれず、必要なライブラリがなければアプリのビルドはエラーになります。

### Windows / PowerShell

```powershell
# 例のパスを実際の Android NDK インストール先に置き換えてください。
./scripts/build-native.ps1 -NdkRoot "C:/Android/android-ndk-r27d"
./gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

`-NdkRoot` を省略した場合、ネイティブビルドスクリプトは `ANDROID_NDK_HOME` または `ANDROID_NDK_ROOT` も参照します。

### Linux / macOS

`ANDROID_NDK_HOME` に NDK のインストール先を設定してから実行します。

```sh
cmake -S native -B native/build-release -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-27 \
  -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
cmake --build native/build-release --target wander_native
mkdir -p app/src/main/jniLibs/arm64-v8a
cp native/build-release/libwander_native.so app/src/main/jniLibs/arm64-v8a/
sh ./gradlew testDebugUnitTest assembleDebug assembleRelease
```

### 出力と署名

| ビルド | 既定の出力先 |
| --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| 署名未設定の Release | `app/build/outputs/apk/release/app-release-unsigned.apk` |

署名付き Release を作成するには、`assembleRelease` の実行前に次の 4 つの環境変数を設定します。

- `WANDERTRACE_RELEASE_KEYSTORE`：キーストアのパス。
- `WANDERTRACE_RELEASE_STORE_PASSWORD`：キーストアのパスワード。
- `WANDERTRACE_RELEASE_KEY_ALIAS`：署名用エイリアス。
- `WANDERTRACE_RELEASE_KEY_PASSWORD`：秘密鍵のパスワード。

Python 3 で生成したパッケージを確認できます。署名を設定した場合は APK のパスを実際の出力先に変更してください。

```sh
python scripts/verify-release.py app/build/outputs/apk/release/app-release-unsigned.apk
```

このスクリプトはモジュールのエントリーポイント、システム専用スコープ、ネイティブブリッジ、地図リソース、ライセンス、テスト・プローブクラスの除外を確認します。実機検証を代替するものではありません。[ビルドの補足説明](docs/BUILDING.md)（中国語）も参照してください。

## ディレクトリ構成と開発

貢献の手順は [CONTRIBUTING.md](CONTRIBUTING.md)（英語）を参照してください。`.github/` に Issue と Pull Request のテンプレートを用意しています。

```text
app/       Android アプリ、モジュール入口、地図リソース、ユニットテスト
native/    ネイティブセンサー実装、検証コード、固定版 Dobby ソース
scripts/   ネイティブビルドと Release パッケージ検査
tests/     単独で実行する Java 検証コード
docs/      実装とビルドの補足説明（中国語）
gradle/    Gradle Wrapper
```

ユニットテストは隔離されたデータを使用し、端末から取得した記録を含まず、Release にも入りません。生成バイナリ、地図 Key、ユーザーのルート、端末ログ、署名情報はコミットしないでください。Gradle Wrapper JAR と第三者ライセンスは保持してください。

Windows では `gradlew.bat clean`、Unix では `sh ./gradlew clean` で Android のビルド出力を削除します。ネイティブビルドのディレクトリと生成された `app/src/main/jniLibs/` は別途削除が必要です。生成物のパスは `.gitignore` に設定されています。

## 無効化と復旧

まずアプリ内でシミュレーションを停止し、必要に応じて LSPosed でモジュールを無効にしてください。ROOT を許可した ADB セッションから `/data/misc/wandertrace/disable` を作成すると、次回のモジュール読み込み時にシステム Hook をスキップできます。このフラグは読み込み時に確認されるため、作成しても既存の Hook は直ちに解除されません。再度有効にする場合はフラグを削除してからモジュールを読み込んでください。システムのシミュレーションが動作中のままアプリを削除しないでください。

## ライセンスと謝辞

本プロジェクト独自のソースコードは **GNU GPL バージョン 3 のみ（GPL-3.0-only）** で公開しています。[LICENSE](LICENSE) を参照してください。第三者コンポーネントには各ライセンスが適用されます。Leaflet は BSD-2-Clause、Leaflet.ChineseTmsProviders は MIT、osmdroid 由来の投影コード、Dobby、libxposed は Apache-2.0 です。地図データとオンラインサービスには、各提供元の利用条件と帰属表示の要件が引き続き適用されます。

[第三者コンポーネントの説明](THIRD_PARTY.md)（中国語）と[ネイティブ依存関係](native/THIRD_PARTY.md)（英語）も参照してください。ライセンス全文は対応するソースとアプリのリソースに含まれています。
