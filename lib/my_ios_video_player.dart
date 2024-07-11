import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const MyPlayView = 'videoplayerview';

class MyIosVideoPlayer extends StatefulWidget {
  const MyIosVideoPlayer({required this.url, super.key});

  final String url;
  @override
  State<MyIosVideoPlayer> createState() => _MyIosVideoPlayerState();
}

class _MyIosVideoPlayerState extends State<MyIosVideoPlayer> {
  final Map<String, dynamic> creationParams = <String, dynamic>{};

  @override
  void initState() {
    super.initState();
    creationParams['url'] = widget.url;
  }

  @override
  Widget build(BuildContext context) {
    return UiKitView(
      viewType: MyPlayView,
      layoutDirection: TextDirection.ltr,
      creationParams: creationParams,
      creationParamsCodec: const StandardMessageCodec(),
    );
  }
}
