MODEL_ID = sherpa-onnx-whisper-tiny.en

# Expected SHA256 checksum (update from sherpa-onnx asr-models/checksum.txt)
CHECKSUM_ARCHIVE = 2bd6cf965c8bb3e068ef9fa2191387ee63a9dfa2a4e37582a8109641c20005dd

download-checksum:
	@echo "Downloading checksum.txt file."
	rm -f checksum.txt
	wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/checksum.txt
	cat checksum.txt | grep $(MODEL_ID).tar.bz2

download-archive:
	@echo "Removing existing bundled model files..."
	rm -f tiny-encoder.int8.onnx tiny-decoder.int8.onnx tiny-tokens.txt
	rm -f tiny.en-encoder.int8.onnx tiny.en-decoder.int8.onnx tiny.en-tokens.txt
	@echo "Downloading model archive..."
	wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$(MODEL_ID).tar.bz2
	@echo "Verifying checksum..."
	@echo "$(CHECKSUM_ARCHIVE)  $(MODEL_ID).tar.bz2" | sha256sum -c -
	@echo "Extracting archive files..."
	tar jxf $(MODEL_ID).tar.bz2
	cp $(MODEL_ID)/tiny.en-encoder.int8.onnx .
	cp $(MODEL_ID)/tiny.en-decoder.int8.onnx .
	cp $(MODEL_ID)/tiny.en-tokens.txt .
	@echo "Cleaning up..."
	rm -rf $(MODEL_ID).tar.bz2 $(MODEL_ID)/
