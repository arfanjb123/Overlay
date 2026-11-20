import os

files = {
    "settings.gradle": "pluginManagement {\n    repositories {\n        google()\n        mavenCentral()\n        gradlePluginPortal()\n    }\n}\ndependencyResolutionManagement {\n    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n    repositories {\n        google()\n        mavenCentral()\n    }\n}\nrootProject.name = \"BenchOverlay\"\ninclude ':app'\n",
    "build.gradle": "plugins {\n    id 'com.android.application' version '8.2.2' apply false\n}\n",
    "gradle.properties": "org.gradle.jvmargs=-Xmx1536m\nandroid.useAndroidX=true\n",
    "app/src/main/res/values/strings.xml": "<resources>\n    <string name=\"app_name\">Notes</string>\n</resources>\n",
}

for path, content in files.items():
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

print("Selesai menulis file dasar:", len(files))
