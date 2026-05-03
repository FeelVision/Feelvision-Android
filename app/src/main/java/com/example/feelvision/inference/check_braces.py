with open('/home/shruthi/AndroidStudioProjects/FeelVision/app/src/main/java/com/example/feelvision/inference/GemmaInferenceManager.kt', 'r') as f:
    content = f.read()
    open_braces = content.count('{')
    close_braces = content.count('}')
    print(f"Open: {open_braces}, Close: {close_braces}")
