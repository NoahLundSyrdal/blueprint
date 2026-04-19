class Warehouse:
    def __init__(self, code: str):
        self.code = code


class Bin:
    def __init__(self, warehouse: Warehouse):
        self.warehouse = warehouse
