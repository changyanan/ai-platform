package com.aiplatform.model.adapter;

import com.aiplatform.model.enums.ProviderProtocol;
import com.aiplatform.model.runtime.ModelRuntime;

/**
 * 语音识别适配接口。P4 接入具体 ASR 供应商，未支持时必须明确报错。
 */
public interface AsrModelAdapter {

    boolean supports(ProviderProtocol protocol);

    AsrTranscription transcribe(ModelRuntime runtime, byte[] audio, String filename, String mimeType);
}
