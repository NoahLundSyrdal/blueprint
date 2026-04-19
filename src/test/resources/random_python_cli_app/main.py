import argparse
from tool.runner import TaskRunner


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("task")
    args = parser.parse_args()
    TaskRunner().run(args.task)


if __name__ == "__main__":
    main()
