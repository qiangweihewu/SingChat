# [Telegram-X](https://github.com/qiangweihewu/SingChat) — SingChat Edition

**Free communication without limits. Telegram meets sing-box proxy.**

This is an enhanced community-driven fork of Telegram X, integrating **[sing-box](https://github.com/SagerNet/sing-box)** to provide seamless proxy support alongside secure native Telegram messaging.

## ✨ What's New: sing-box Integration

Unlike the original Telegram X, this version adds built-in proxy capabilities:

- **Multi-Protocol Support**: VLESS, VMess, Trojan, and more proxy protocols in one app
- **Zero-Configuration Connection**: Import proxy nodes with QR code or direct URL
- **Smart Routing**: Customize which apps and domains use proxy
- **One-Click Management**: Easily switch between multiple proxy nodes
- **Privacy-First Design**: End-to-end encryption + proxy layer protection

## Original Description

This is the complete source code and build instructions for an enhanced Android client for the Telegram messenger, based on the [Telegram API](https://core.telegram.org/api) and the [MTProto](https://core.telegram.org/mtproto) secure protocol via [TDLib](https://github.com/TGX-Android/tdlib), with added sing-box proxy support.

### 📥 Installation

* [**GitHub Releases**](https://github.com/qiangweihewu/SingChat/releases) — Download APKs directly
* Build from source — Follow instructions below for full control

### 🔗 Project Links

* **Upstream**: [Telegram X](https://github.com/TGX-Android/Telegram-X) - Original project by TGX team
* **sing-box Core**: [sing-box](https://github.com/SagerNet/sing-box) - Powerful proxy platform
* **TDLib**: [Telegram Database Library](https://github.com/tdlib/td) - Telegram protocol implementation


## 🚀 Key Features

### Secure Messaging (Native Telegram)
- End-to-end encrypted chats, calls, and media sharing
- All original Telegram X features and optimizations
- Clean, intuitive interface for power users

### Proxy Freedom (sing-box powered)
- **Import from anywhere**: QR codes, URLs, Clash configs
- **Protocol flexibility**: VLESS, VMess, Trojan, Shadowsocks, HTTP, SOCKS5, and more
- **Smart routing**: Route specific apps or domains through proxy
- **Connection monitoring**: Real-time speed tests and latency tracking
- **Node switching**: Test and switch between multiple nodes instantly

### Privacy & Control
- No logging, no tracking
- Open source — audit the code yourself
- Stays lean: uses sing-box's proven proxy engine
- Configure exactly how apps connect

## 💡 Use Cases

- **Journalists & Activists**: Combine Telegram's security with proxy freedom
- **Travelers**: Protect your communication across borders
- **Remote Workers**: In regions with network restrictions
- **Privacy Advocates**: True control over your internet connection

## Build instructions

### Prerequisites

* At least **5,34GB** of free disk space: **487,10MB** for source codes and around **4,85GB** for files generated after building all variants
* **4GB** of RAM
* **macOS** or **Linux**-based operating system. **Windows** platform is supported by using [MSYS](https://www.msys2.org/) (e.g., [Git Bash](https://gitforwindows.org/)).

#### macOS

* [Homebrew](https://brew.sh)
* git with LFS, wget and sed: `$ brew install git git-lfs wget gsed && git lfs install`

#### Ubuntu

* git with LFS: `# apt install git git-lfs`
* Run `$ git lfs install` for the current user, if you didn't have `git-lfs` previously installed

#### Windows

* **Telegram X** does not provide official build instructions for Windows platform. It is recommended to rely on Linux distributions instead.

### Building

1. `$ git clone --recursive --depth=1 --shallow-submodules https://github.com/qiangweihewu/SingChat.git singchat` — clone **SingChat** with submodules (includes sing-box)
2. In case you forgot the `--recursive` flag, `cd` into `singchat` directory and: `$ git submodule init && git submodule update --init --recursive --depth=1`
3. Create `keystore.properties` file outside of source tree with the following properties:<br/>`keystore.file`: absolute path to the keystore file<br/>`keystore.password`: password for the keystore<br/>`key.alias`: key alias that will be used to sign the app<br/>`key.password`: key password.<br/>**Warning**: keep this file safe and make sure nobody, except you, has access to it. For production builds one could use a separate user with home folder encryption to avoid harm from physical theft
4. `$ cd singchat`
5. Run `$ scripts/./setup.sh` and follow up the instructions
6. If you specified package name that's different from the default one, [setup Firebase](https://firebase.google.com/docs/android/setup) and replace `google-services.json` with the one that's suitable for the `app.id` you need
7. Now you can open the project using **[Android Studio](https://developer.android.com/studio/)** or build manually from the command line: `./gradlew assembleUniversalRelease`.

> **Note**: sing-box integration is built-in and compiled with the app. No additional configuration needed — just build and run!

#### Available flavors

* `arm64`: **arm64-v8a** build with `minSdkVersion` set to `21` (**Lollipop**)
* `arm32`: **armeabi-v7a** build
* `x64`: **x86_64** build with `minSdkVersion` set to `21` (**Lollipop**)
* `x86`: **x86** build
* `universal`: universal build that includes native bundles for all platforms.

### Quick setup for development

If you are developing a contribution to the project, you may follow the simpler building steps:

1. `$ git clone --recursive https://github.com/qiangweihewu/SingChat.git singchat`
2. `$ cd singchat`
3. [Obtain Telegram API credentials](https://core.telegram.org/api/obtaining_api_id)
4. Create `local.properties` file in the root project folder using any text editor:<br/><pre># Location where you have Android SDK installed
sdk.dir=YOUR_ANDROID_SDK_FOLDER
\# Telegram API credentials obtained at previous step
telegram.api_id=YOUR_TELEGRAM_API_ID
telegram.api_hash=YOUR_TELEGRAM_API_HASH</pre>
5. Run `$ scripts/./setup.sh` — this will download required Android SDK packages and build native dependencies (including sing-box)
6. Open and build project via [Android Studio](https://developer.android.com/studio) or by using one of `./gradlew assemble` commands in terminal

## 🤝 Contributing

Contributions are welcome! Whether it's:
- Bug reports and fixes
- Feature improvements
- Documentation updates
- sing-box integration enhancements

Please refer to the [pull request guidelines](docs/PULL_REQUEST_TEMPLATE.md) before submitting.

**Note**: This is a community fork. For issues related to core Telegram functionality, consider checking [upstream](https://github.com/TGX-Android/Telegram-X) first.

## Reproducing public builds

In order to verify that there is no additional source code injected inside official APKs, you must use one of the following versions of **Ubuntu**:

* **21.04**: for builds published before [26th May 2023](https://github.com/TGX-Android/Telegram-X/commit/e9a054a0f469a98a13f7e0d751539687fef8759b)
* **22.04.2 LTS**: for builds published before 27th September 2025
* **24.04 LTS**: for any newer releases.

And update its configuration:

1. Create user called `vk` with the home directory located at `/home/vk`
2. Clone repository to `/home/vk/singchat`
3. Check out the specific commit you want to verify
4. In rare cases of builds that include unmerged pull requests, you must follow actions performed by [Publisher's](https://github.com/TGX-Android/Publisher/blob/main/main.js) `fetchPr` and `squashPr` tasks
5. `cd` into `singchat` folder and install dependencies: `# apt install $(cat reproducible-builds/dependencies.txt)`
6. Follow up the build instruction from the previous section
7. Run `$ apkanalyzer apk compare --different-only <remote-apk> <reproduced-apk>`
8. If only signature files and metadata differ, build reproduction is successful.

## 📜 License

This project combines:
- **Telegram X**: Licensed under GNU General Public License v2
- **sing-box**: Licensed under GNU General Public License v2
- **TDLib**: Licensed under Boost Software License

See [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Telegram X team](https://github.com/TGX-Android/Telegram-X) — Original project foundation
- [sing-box](https://github.com/SagerNet/sing-box) — Powerful proxy platform
- [TDLib](https://github.com/tdlib/td) — Telegram protocol implementation
- All contributors and community members

## ⚠️ Disclaimer

This is a community-driven fork and is **not** affiliated with Telegram. Use at your own risk. For official Telegram support, visit [telegram.org](https://telegram.org/).

---

Made with ❤️ for privacy and freedom of communication.

<i>PS: [Docker](https://www.docker.com) is not considered an option, as it just hides away these tasks, and requires that all published APKs must be built using it.</i>

## Verifying side-loaded APKs

If you downloaded **Telegram X** APK from somewhere and would like to simply verify whether it's an original APK without any injected malicious source code, you need to get checksum (`SHA-256`, `SHA-1` or `MD5`) of the downloaded APK file and find whether it corresponds to any known **Telegram X** version.

In order to obtain **SHA-256** of the APK:

* `$ sha256sum <path-to-apk>` on **Ubuntu**
* `$ shasum -a 256 <path-to-apk>` on **macOS**
* `$ certutil -hashfile <path-to-apk> SHA256` on **Windows**

Once obtained, there are three ways to find out the commit for the specific checksum:

* Sending checksum to [`@tgx_bot`](https://t.me/tgx_bot)
* Searching for a checksum in [`@tgx_log`](https://t.me/tgx_log). You can do so without need in installing any Telegram client by using this URL format: [`https://t.me/s/tgx_log?q={checksum}`](https://t.me/s/tgx_log?q=c541ebb0a3ae7bb6e6bd155530f375d567b8aef1761fdd942fb5d69af62e24ae) (click to see in action). Note: unpublished builds cannot be verified this way.

## License

`Telegram X` is licensed under the terms of the GNU General Public License v3.0.

For more information, see [LICENSE](/LICENSE) file.

License of components and third-party dependencies it relies on might differ, check `LICENSE` file in the corresponding folder.

### Third-party dependencies

List of third-party components used in **Telegram X** can be found [here](/docs/THIRDPARTY.md). Additionally you can check the specific commit of the third-party component used, for example, [here](/app/jni/thirdparty) and [here](/thirdparty).

## Contributions

**Telegram X** welcomes contributions. Check out [pull request template](/docs/PULL_REQUEST_TEMPLATE.md) and [guide for contributors](/docs/GUIDE.md) to learn more about Telegram X internals before creating the first pull request.

If you are a regular user and experience a problem with Telegram X, the best place to look for solution is [Telegram X chat](https://t.me/tgandroidtests) — a community with over 4 thousand members. Please do not use this repository to ask questions: if you have general issue with Telegram, refer to [FAQ](http://telegram.org/faq) or contact [Telegram Support](https://telegram.org/faq#telegram-support).
