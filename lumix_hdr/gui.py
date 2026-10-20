"""Small desktop interface; install OS python-tk on Linux if necessary."""
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from pathlib import Path
from .core import merge


def run():
    app = tk.Tk()
    app.title("Lumix HDR Companion")
    app.geometry("620x330")
    paths = []
    mode = tk.StringVar(value="hdr")
    status = tk.StringVar(value="3, 5 oder 7 JPEGs einer Belichtungsreihe wählen.")
    ttk.Label(app, text="Lumix HDR Companion", font=("sans", 20)).pack(pady=20)
    ttk.Label(app, text="Lokal verarbeiten · Originale behalten · Keine Cloud").pack()
    def choose():
        nonlocal paths
        paths = [Path(p) for p in filedialog.askopenfilenames(filetypes=[("JPEG", "*.jpg *.JPG *.jpeg")])]
        status.set(f"{len(paths)} Bilder ausgewählt")
    ttk.Button(app, text="Belichtungsreihe wählen", command=choose).pack(pady=12)
    ttk.Radiobutton(app, text="Radiance-HDR + JPEG-Vorschau (EXIF nötig)", variable=mode, value="hdr").pack()
    ttk.Radiobutton(app, text="Belichtungsfusion als JPEG", variable=mode, value="fusion").pack()
    def start():
        dest = filedialog.askdirectory(title="Ausgabeordner")
        if not dest:
            return
        selected, selected_mode = list(paths), mode.get()
        button.configure(state="disabled")
        status.set("Verarbeitung läuft …")
        def work():
            try:
                result = merge(selected, Path(dest), selected_mode)
                app.after(0, lambda: status.set("Gespeichert: " + Path(result["jpeg"]).name))
            except Exception as error:
                text = str(error)
                app.after(0, lambda: messagebox.showerror("Verarbeitung", text))
            finally:
                app.after(0, lambda: button.configure(state="normal"))
        threading.Thread(target=work, daemon=True).start()
    button = ttk.Button(app, text="HDR erstellen", command=start)
    button.pack(pady=14)
    ttk.Label(app, textvariable=status, wraplength=580).pack()
    app.mainloop()
