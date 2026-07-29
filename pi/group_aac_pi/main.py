import logging

from group_aac_pi.runtime import (
    PiConfig,
    PubNubPiRuntime,
)


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format=(
            "%(asctime)s "
            "%(levelname)s "
            "%(name)s: "
            "%(message)s"
        ),
    )

    config = PiConfig.from_environment()
    runtime = PubNubPiRuntime(config)

    runtime.start()
    runtime.run_forever()


if __name__ == "__main__":
    main()