# VoltForge setup (phone only)

1. Termux (F-Droid build): `pkg install openjdk-17 git python`
2. Keystore (keep this file forever, updates need the same key):
   `keytool -genkeypair -keystore ks.jks -alias voltforge -keyalg RSA -keysize 2048 -validity 10000 -storepass CHOOSEPASS -keypass CHOOSEPASS -dname "CN=VoltForge"`
   `base64 -w0 ks.jks` -> copy the output
3. GitHub: new PUBLIC repo. Settings > Secrets and variables > Actions: add `KS_B64` (the base64) and `KS_PASS` (CHOOSEPASS).
4. Push this folder: `git init && git add . && git commit -m init && git branch -M main && git remote add origin URL && git push -u origin main` (use a personal access token as password).
5. Actions tab: the build runs and publishes `voltforge.apk` as a release. If it fails, open the run log and send me the error.
6. Host: `curl -L -o site/voltforge.apk https://github.com/USER/REPO/releases/latest/download/voltforge.apk` then `sh serve.sh`, open http://localhost:8080 in your phone browser.
7. In the app: grant System settings control and Usage access. Deep mode needs one `adb shell pm grant` from a borrowed computer.
