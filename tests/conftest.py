import pytest
from typing import Any, Iterator

from helpers import Browser


def pytest_addoption(parser: Any) -> None:
    parser.addoption("--headless", action="store_true")


@pytest.fixture
def headless(request: Any) -> bool:
    option: bool = request.config.getoption("--headless")
    return option


@pytest.fixture()
def browser(headless: bool) -> Iterator[Browser]:
    browser = Browser(headless=headless)
    yield browser
    browser.close()
