package com.example.server.open;

import com.example.server.internal.内部API;

public class 公開API {
  public static String 実行() {
    return 内部API.実行();
  }
}
