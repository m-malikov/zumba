package udp

type Message struct {
	Conference uint8
	User uint8
	Content []byte
}

func ParseMessageFromBytes(bytes []byte) (*Message, error) {
	return &Message{
		Conference: bytes[0],
		User: bytes[1],
		Content: bytes,
	}, nil
}