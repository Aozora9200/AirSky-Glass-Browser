class Dialog:
    command = None

    def __init__(self, master=None, **options):
        self.master = master
        self.options = options

    def show(self, **options):
        return None
