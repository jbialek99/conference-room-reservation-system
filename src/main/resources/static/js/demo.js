document.addEventListener("DOMContentLoaded", () => {
    const hall = document.getElementById("hall");
    const title = document.getElementById("title");
    const start = document.getElementById("start");
    const end = document.getElementById("end");
    const events = document.getElementById("events");
    const message = document.getElementById("message");
    const reserveButton = document.getElementById("reserve");
    const csrf = document.querySelector('meta[name="csrf-token"]').content;

    const headers = {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": csrf
    };

    const formatDate = value => new Date(value).toLocaleString("pl-PL", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    });

    function notify(text, type = "info") {
        message.textContent = text;
        message.className = "notice " + type;
        message.hidden = false;
    }

    async function api(url, options = {}) {
        const response = await fetch(url, options);
        const text = await response.text();

        let data;
        try {
            data = JSON.parse(text);
        } catch {
            data = text;
        }

        if (!response.ok) {
            if (response.status === 409) {
                throw new Error("Wybrany termin jest już zajęty. Wybierz inną godzinę lub salę.");
            }
            if (response.status === 403) {
                throw new Error("Brak uprawnień do wykonania tej operacji.");
            }
            if (response.status === 404) {
                throw new Error("Nie znaleziono rezerwacji lub nie możesz jej anulować.");
            }
            throw new Error(typeof data === "object" && data !== null
                ? data.error || "Nie udało się wykonać operacji."
                : "Nie udało się wykonać operacji.");
        }

        return data;
    }

    async function loadHalls() {
        const rooms = await api("/api/demo/halls");
        hall.replaceChildren();

        for (const room of rooms) {
            const option = document.createElement("option");
            option.value = room.id;
            option.textContent = room.name;
            hall.append(option);
        }

        await loadEvents();
    }

    async function loadEvents() {
        events.replaceChildren();

        if (!hall.value) {
            events.textContent = "Brak dostępnych sal.";
            return;
        }

        const items = await api("/api/reservations/hall/" + encodeURIComponent(hall.value));
        items.sort((a, b) => new Date(a.start) - new Date(b.start));

        if (items.length === 0) {
            const empty = document.createElement("div");
            empty.className = "empty-state";
            empty.textContent = "Ta sala nie ma jeszcze rezerwacji DEMO.";
            events.append(empty);
            return;
        }

        for (const item of items) {
            const card = document.createElement("div");
            card.className = "event-card";

            const details = document.createElement("div");
            const name = document.createElement("div");
            name.className = "event-name";
            name.textContent = item.title;

            const dates = document.createElement("div");
            dates.className = "event-dates";
            dates.textContent = formatDate(item.start) + " — " + formatDate(item.end);

            details.append(name, dates);

            const cancel = document.createElement("button");
            cancel.type = "button";
            cancel.className = "cancel-button";
            cancel.textContent = "Anuluj";

            cancel.addEventListener("click", async () => {
                if (!confirm("Czy na pewno chcesz anulować tę rezerwację?")) return;

                cancel.disabled = true;

                try {
                    await api("/api/reservations/delete/" + encodeURIComponent(item.id), {
                        method: "DELETE",
                        headers
                    });
                    notify("Rezerwacja została anulowana.", "success");
                    await loadEvents();
                } catch (error) {
                    notify(error.message, "error");
                    cancel.disabled = false;
                }
            });

                        card.append(details);

            if (item.canCancel === true) {
                card.append(cancel);
            }
            events.append(card);
        }
    }

    document.getElementById("reservation-form").addEventListener("submit", async event => {
        event.preventDefault();

        const startDate = new Date(start.value);
        const endDate = new Date(end.value);

        if (!title.value.trim() || !start.value || !end.value ||
            !Number.isFinite(startDate.getTime()) || !Number.isFinite(endDate.getTime())) {
            notify("Wypełnij wszystkie pola formularza.", "error");
            return;
        }

        if (startDate <= new Date()) {
            notify("Początek rezerwacji musi być w przyszłości.", "error");
            return;
        }

        if (endDate <= startDate) {
            notify("Zakończenie musi być późniejsze niż rozpoczęcie.", "error");
            return;
        }

        reserveButton.disabled = true;
        reserveButton.textContent = "Zapisywanie...";

        try {
            await api("/api/reservations/hall/" + encodeURIComponent(hall.value) + "/reserve", {
                method: "POST",
                headers,
                body: JSON.stringify({
                    title: title.value.trim(),
                    startMeeting: startDate.toISOString(),
                    endMeeting: endDate.toISOString()
                })
            });

            notify("Sala została pomyślnie zarezerwowana.", "success");
            await loadEvents();
        } catch (error) {
            notify(error.message, "error");
        } finally {
            reserveButton.disabled = false;
            reserveButton.textContent = "Zarezerwuj salę";
        }
    });

    document.getElementById("refresh").addEventListener("click", () =>
        loadEvents().catch(error => notify(error.message, "error"))
    );

    hall.addEventListener("change", () =>
        loadEvents().catch(error => notify(error.message, "error"))
    );

    loadHalls().catch(error => notify(error.message, "error"));
});