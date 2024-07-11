import Flutter
import UIKit

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {

  var screenRecorder: ScreenRecorder!
  var myUrl: URL!

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)

    let controller : FlutterViewController = window?.rootViewController as! FlutterViewController

    let keychainChannel = FlutterMethodChannel(name:"recordPlatform",binaryMessenger:controller.binaryMessenger)

    screenRecorder = ScreenRecorder()

    keychainChannel.setMethodCallHandler({
      (call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
      if (call.method == "start") {
        if let documentDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
          let uniqueFileName = "myVideo\(UUID()).mp4"
          let destinationURL = documentDirectory.appendingPathComponent(uniqueFileName)
          self.myUrl = destinationURL
          self.screenRecorder.startRecording(to: destinationURL,
          saveToCameraRoll: true,
          errorHandler:{error in 
            debugPrint("Error when recording \(error)")
          })
        }
      }else if call.method == "stop" {
        self.screenRecorder.stopRecording(errorHandler:{ error in
            debugPrint("Error when stop recording \(error)")
        })
        result("\(self.myUrl!)")
      }else{
        result(FlutterMethodNotImplemented)
        return
      }
    })


    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
