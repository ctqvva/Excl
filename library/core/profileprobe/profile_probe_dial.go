package libexclavecore

import (
	"context"
	"net"

	v2net "github.com/exclavenetwork/exclave-core/v5/common/net"
)

func probeDial(instance *V2RayInstance, ctx context.Context, network, address string) (net.Conn, error) {
	destination, err := v2net.ParseDestination(network + ":" + address)
	if err != nil {
		return nil, err
	}
	return instance.dial(ctx, destination)
}
