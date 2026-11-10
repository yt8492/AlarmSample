# AlarmSample

- 日本語で応答する。技術同人誌「Mayamito Techbook Vol.4」と対応するサンプルである。
- 通常のKotlin Androidプロジェクトとし、Kotlin MultiplatformやMedia3は導入しない。
- UIはJetpack ComposeとMaterial 3で実装する。編集中の設定と音源の取り込みはViewModelで管理し、画面再生成で失われないようにする。
- アラーム1件の予約、曜日指定、MediaPlayerの鳴動を中心に保つ。書籍の説明に不要な機能や依存関係を増やさない。
- 掲載コードに合わせ、Kotlinのインデントはスペース2個を使う。
- 変更時は隣の`../MayamitoTechbook4/articles/alarm.re`との対応も確認する。抜粋を変える場合は、原稿と`scripts/sample-code.json`も更新する。
- JDK 17で`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`を実行する。ビルドは、合理的な理由がない限り完了を待つ。
- 日時計算の検証と、端末での配信・通知・再生の検証を区別する。実施した確認のみを報告する。
- コミットは依頼された場合に行い、コミットメッセージは日本語にする。
