# AlarmSample

「Mayamito Techbook Vol.4」に対応する目覚ましアプリのサンプルです。Kotlin Androidの単一 `app` モジュールで実装し、画面にはJetpack ComposeとMaterial 3、音声再生にはMediaPlayerを使います。

Kotlin Multiplatform、Media3、サーバー通信、データベースは使いません。

## できること

- アラーム1件の時刻・繰り返す曜日・音源を設定する。
- 曜日指定なしでは次の指定時刻に1回だけ鳴らす。
- 曜日指定ありでは次の1回を `setAlarmClock()` で予約し、鳴動開始時に次回を予約する。
- 標準アラーム音、またはアプリへ取り込んだ音楽をループ再生する。
- 通知や鳴動画面から停止する。予約の解除と鳴動の停止は別の操作。
- 読めない音源は標準音、同梱の代替音へ順に切り替える。
- 最大10分で鳴動を終了し、MediaPlayer・音声フォーカス・WakeLockを解放する。
- 端末再起動、時刻やタイムゾーンの変更、アプリ更新、正確なアラームの許可付与時に予約を復元する。
- 通常の画面再表示では保存済みの今回分を維持し、配信待ちの予約を次回へ進めない。
- ReceiverからサービスへWakeLockを引き継ぎ、サービス起動前のスリープを防ぐ。
- 別アプリからduckingの要求を受けた場合は一時停止し、フォーカスが戻ると再開する。

## 環境

| 項目 | バージョン |
| --- | --- |
| JDK | 17 |
| Gradle | 8.13（Wrapper同梱） |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.21 |
| AndroidX Core / Activity | 1.17.0 / 1.12.1 |
| Compose BOM | 2026.04.01 |
| Compose Compiler Gradleプラグイン | 2.2.21（Kotlinと同じ） |
| AndroidX Lifecycle | 2.10.0 |
| UIテスト（Espresso） | 3.7.0 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 26 |

バージョンは書籍用に固定しています。最新版を示すものではありません。

## 起動する

1. Android Studioでこのディレクトリを開き、JDK 17とAndroid SDK 36を設定する。
2. Gradleの同期後、`app`をAndroid 8.0以降の端末で実行する。
3. 「通知の許可・設定」と「アラームとリマインダーの許可」を確認する。
4. 必要に応じて「ロック画面での全画面表示の許可」を設定する。未許可でも通知から操作できる。
5. 現在より1〜2分後の時刻を選び、「保存して予約」を押す。権限設定から戻った場合も、このボタンを押し直す。
6. ホーム画面へ戻って待ち、鳴動後に通知や画面の「停止」を押す。

コマンドラインでは以下を実行できます。

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`にローカルのSDKパスを指定できます。このファイルとビルド成果物はGit管理から除外しています。

## 音源

「音楽ファイルを選ぶ」でStorage Access Frameworkから選択します。ファイルはバックグラウンドで内部ストレージへコピーし、選択元のURIを後日の再生には使いません。取り込み上限は20 MiBです。

ストレージ全体への読み取り権限は要求しません。取り込み後に「保存して予約」を押すと設定が保存されます。音源は端末の対応コーデックで再生できる必要があります。

`app/src/main/res/raw/fallback_alarm.wav`は、このサンプル向けに生成した短い電子音です。外部の楽曲は含めていません。音量は端末のアラーム音量に従い、アプリから最大音量への変更は行いません。

## 主なファイル

| ファイル | 役割 |
| --- | --- |
| `Alarm.kt` | 時刻・曜日・音源・予約日時のモデル |
| `NextAlarmCalculator.kt` | 次回の鳴動日時の計算 |
| `AlarmStore.kt` | 1件の設定をSharedPreferencesへ保存 |
| `AlarmScheduler.kt` | PendingIntentとAlarmManagerの操作 |
| `AlarmCoordinator.kt` | 予約・配信の消費・復元の整合 |
| `AlarmReceiver.kt` | 配信を受けて次回予約とサービス起動 |
| `AlarmLaunchWakeLock.kt` | ReceiverからサービスへのWakeLock引き継ぎ |
| `AlarmRingingService.kt` | 通知・鳴動・10分の上限・WakeLock |
| `AlarmPlayer.kt` | MediaPlayer、音声フォーカス、代替音、解放 |
| `SoundStore.kt` | 音源の取り込みと内部ファイルの解決 |
| `RescheduleReceiver.kt` | 再起動・時刻変更等で予約を復元 |
| `MainActivity.kt` / `RingingActivity.kt` | 設定画面 / 停止画面 |
| `AlarmScreen.kt` / `AlarmTheme.kt` | Composeの画面・時刻入力・Material 3テーマ |
| `AlarmViewModel.kt` | 編集状態、予約操作、音源の取り込み |

上記Kotlinファイルは `app/src/main/java/com/yt8492/alarmsample/` 以下にあります。

画面は状態とコールバックを受け取るComposableとして実装しています。Activityでは`setContent()`で表示し、ViewModelの`StateFlow`を`collectAsStateWithLifecycle()`で購読します。編集中の時刻・曜日・音源は`SavedStateHandle`へ保存し、音源取り込みは`viewModelScope`と`Dispatchers.IO`で実行します。画面再生成では取り込みを継続し、プロセス終了後の取り込み再開は対象外です。

## 意図して限定していること

- 電源OFF中は鳴りません。Direct Bootには未対応なので、再起動後の最初のロック解除より前の鳴動は対象外です。
- 再起動等で既に過ぎた単発予約は無効にします。過去の鳴動をまとめて再実行しません。
- 画面再表示では未来の予約を同じ日時で再登録し、過去の未消費分は配信待ちとして保持します。配信待ちと失われた予約を日時だけでは区別できないため、過去分を画面再表示から新たに即時配信することはしません。
- 繰り返しは現在のタイムゾーンで再計算し、単発は保存した絶対時刻を保ちます。
- 夏時間で存在しない時刻は `java.time.atZone()` の規則で先へ進め、重複時刻は早い側のオフセットを使います。
- 通知の許可は、停止操作を提供するために予約の前提としています。OSがFGS起動のために必須としている条件ではありません。
- 音声フォーカスの取得待ちは再生せず待機します。一時的な喪失では一時停止し、恒久的な喪失や取得失敗では停止します。
- WakeLockの引き継ぎはReceiverとサービスが同一プロセスのメインスレッドで動く前提です。起動要求ごとのトークンを渡し、鳴動用の取得後に引き継ぎ用を解放します。起動失敗時は即時解放し、未起動時に備えた上限は30秒です。
- プロセスが破棄された後の鳴動再開、強制停止の回避、メーカー独自の省電力制限の回避は行いません。
- 1件の小さな設定なので同期保存を使っています。OSへの予約と設定保存の原子性までは保証しません。
- 設定画面は日本語の作例です。ローカライズや複数アラーム対応は含めていません。

## 検証

日時計算と古い配信の照合に関するJVMテストを14件用意しています。OSの配信、通知、MediaPlayerの動作はJVMテストとは別に確認します。

### 実機テスト

`AlarmScreenTest`の3件では、時刻・曜日の編集後と音源取り込み中の画面再生成、バックグラウンドでの単発アラーム配信、Composeの鳴動画面での停止を確認します。`AlarmBehaviorTest`の8件では、復元と配信の順序、WakeLockの引き継ぎ・失敗・期限、別UIDからのducking要求を確認します。USBデバッグを許可した実機を接続して実行してください。`DEVICE_SERIAL`は`adb devices`で確認した実機のシリアル番号へ置き換えます。

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w com.yt8492.alarmsample.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_SERIAL uninstall com.yt8492.alarmsample.test
```

端末のロックは事前に解除し、通知と正確なアラームの許可を設定します。鳴動テストは次の分の時刻へ予約するため、最大1分程度待機します。既存の予約が有効なら鳴動テストはスキップし、実行した場合は終了後に元の設定を戻します。音源取り込みにはテストAPK専用の遅延配信を使い、画面再生成を挟んでコピーの一致まで確認します。

バックグラウンド鳴動テストは画面を消灯するため、画面ロックのある端末では後続のテストがスキップされる場合があります。ロックを解除して、対象のテストを個別に実行し直してください。音源取り込みのテストだけを再実行する場合は、次のように指定します。

```sh
adb -s DEVICE_SERIAL shell am instrument -w -e class com.yt8492.alarmsample.AlarmScreenTest#soundImportCompletesAfterRecreation com.yt8492.alarmsample.test/androidx.test.runner.AndroidJUnitRunner
```

Espressoは3.7.0をテストの依存関係へ明示しています。Composeのテストライブラリから間接的に選ばれる3.5.0では、Android 17で`InputManager.getInstance()`への参照に失敗するためです。3.7.0にはこの参照を`getSystemService()`へ変更した修正が含まれます。詳細は[AndroidX Testのリリースノート](https://developer.android.com/jetpack/androidx/releases/test)を参照してください。

### 復元・WakeLock・duckingの検証方法

- 配信が遅れる順序は、期限を過ぎた未消費の設定を書き、設定画面を再生成してから実際のReceiverを呼んで再現します。単発・繰り返しともMediaPlayerの実再生まで確認します。未来の予約は保存日時とOSの次回予約が一致することを確認します。
- システムからの復元は、実際の再起動や時計変更を行う代わりに、対象のIntentを`RescheduleReceiver`へ渡し、過去の単発の無効化と繰り返しの再計算を確認します。これは再起動そのものの動作確認とは区別します。
- WakeLockは実機の`dumpsys power`で取得・解放を確認します。起動要求の直前とサービス開始後の状態を調べ、未取得の鳴動用WakeLockへの引き継ぎを拒否することも確認します。起動失敗はContextのラッパーで再現し、未起動時の期限も短く指定して検証します。
- duckingにはテストAPKの別UIDで動くJavaのActivityを使います。同じUID内のフォーカス要求ではOSの自動ducking経路を確認できないためです。相手の要求が許可されることと、実際のMediaPlayerの一時停止・再開を確認します。`setWillPauseWhenDucked(true)`を指定し、一時停止の通知を受け取ります。

テスト用のActivityと音源プロバイダーはテストAPKだけに含まれます。検証後にテストAPKを削除することで、端末からも取り除けます。

### 確認結果（2026年11月10日）

- JDK 17でJVMテスト14件が成功。APK作成と `lintDebug` も成功（Lintのエラー0件）。固定した依存バージョンの更新案内や、日本語の作例UIに関する警告は残っています。
- Pixel 3実機、Android 12（API 31）で確認しました。
- Compose UIテスト3件が成功しました。時刻・曜日の編集と音源取り込みが画面再生成をまたいで保持されること、画面消灯中の単発予約で音声再生が開始し、Composeの鳴動画面から停止できることを確認しました。
- 復元・WakeLock・duckingの修正後、`AlarmBehaviorTest`の8件とCompose UIの3件をまとめた実機テスト11件が成功しました。配信が遅れる順序でも単発・繰り返しが鳴り、WakeLockの引き継ぎ・起動失敗時の解放・期限、別UIDからのduckingでの一時停止と再開を確認しました。
- 曜日指定なしの予約がホーム画面表示中に配信され、フォアグラウンドサービスと `USAGE_ALARM` のMediaPlayerが再生を開始しました。単発設定が無効になることと、通知の「停止」からサービスが終了することを確認しました。標準アラーム音はスピーカーからの聴取も確認済みです。
- Storage Access FrameworkからWAVファイルを選択し、内部ストレージのコピーが元ファイルと一致することを確認しました。選択元のテストファイルを削除した後も、取り込んだ音源の設定で再生が開始しました。
- 火曜日のみを指定した予約で、鳴動開始時に1週間後の火曜日の同時刻がOSへ予約されることを確認しました。通知から停止した後も、その次回予約は残っていました。
- 確認後は画面の「予約を解除」から次回分を解除し、鳴動サービスが停止していることを確認しました。公開ストレージに置いたテスト音源も削除済みです。

実機での再起動、最初のロック解除前、長時間スリープ、通話との競合は未確認です。日時計算の夏時間・年またぎなどはJVMテストで確認しており、実機の時計を変更しての検証は行っていません。

### 追加の実機確認（2026年11月11日）

- Pixel 10 Pro、Android 17（API 37）、ビルド`DP11.261016.003`で確認しました。サンプルのcompileSdk / targetSdkは36のままです。
- Espresso 3.7.0を明示した構成で、動作確認8件とCompose UIテスト3件の計11件が成功しました。音源取り込みの1件は画面ロックでスキップされたため、ロック解除後に個別に再実行して成功しています。
- 画面消灯中に予約した単発アラームが配信され、`USAGE_ALARM`のMediaPlayer再生を開始し、Composeの鳴動画面から停止できることを確認しました。端末のスピーカーから実際に音が聞こえることも確認済みです。
- 予約復元で未消費の今回分を保つこと、WakeLockの引き継ぎ・起動失敗時の解放・期限、別UIDからのduckingでの一時停止と再開、編集状態と音源取り込みの画面再生成への対応を確認しました。取り込んだ音源のコピーは元のデータと一致しました。
- 検証後はアラームを初期設定の無効状態へ戻し、鳴動サービスの停止とWakeLockの解放を確認しました。

## 原稿との対応

本書リポジトリを隣の `../MayamitoTechbook4` に置いた場合、次のコマンドで掲載コード28件との一致を確認できます。

```sh
python3 ../MayamitoTechbook4/scripts/check-sample-code.py --sample .
```

元の設計は `wake-up-with-mobile` のAndroid実装を参考にしています。本サンプルではKMPとアプリ固有の同期処理を取り除き、標準の `java.time` を使用しています。また、曜日指定の次回予約は、元の停止時から鳴動開始時へ変更しています。
