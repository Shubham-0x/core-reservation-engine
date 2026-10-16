
const API = "/api";

const $ = (id) => document.getElementById(id);

const eventSelect = $("eventSelect");
const showIdInput = $("showId");
const loadSeatsButton = $("loadSeats");
const seatArea = $("seatArea");
const seatGrid = $("seatGrid");
const bookButton = $("bookButton");
const selectionText = $("selectionText");
const eventInfo = $("eventInfo");
const bookingIdInput = $("bookingId");
const cancelButton = $("cancelButton");
const toast = $("toast");

let currentSeats = [];
let selectedSeatId = null;
let toastTimer;
let authHeader = null;
let authenticatedEmail = null;

function showToast(message, isError = false) {
    toast.textContent = message;
    toast.classList.toggle("error", isError);
    toast.classList.add("show");

    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toast.classList.remove("show");
    }, 3500);
}

async function apiRequest(url, options = {}) {
    const headers = new Headers(options.headers || {});

    if (authHeader) {
        headers.set("Authorization", authHeader);
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    const text = await response.text();
    let data = null;

    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            data = text;
        }
    }

    if (!response.ok) {
        const message =
            typeof data === "object" && data !== null
                ? data.message || data.error || data.detail
                : data;

        const error = new Error(
            message || `Request failed with HTTP ${response.status}`
        );

        error.status = response.status;
        throw error;
    }

    return data;
}

// Credentials are kept only in memory, not in localStorage.
async function ensureAuthenticated() {
    if (authHeader && authenticatedEmail) {
        return true;
    }

    const email = prompt("Enter your registered email:");

    if (!email || !email.trim()) {
        showToast("Login required to continue.", true);
        return false;
    }

    const password = prompt("Enter your password:");

    if (password === null || password.length === 0) {
        showToast("Password is required.", true);
        return false;
    }

    const basicToken = btoa(`${email.trim()}:${password}`);
    const temporaryHeader = `Basic ${basicToken}`;

    try {
        const response = await fetch(`${API}/auth/login`, {
            method: "POST",
            headers: {
                Authorization: temporaryHeader
            }
        });

        const text = await response.text();
        let data = {};

        try {
            data = text ? JSON.parse(text) : {};
        } catch {
            data = { message: text };
        }

        if (!response.ok) {
            throw new Error(
                data.message || data.error || "Login failed. Check your credentials."
            );
        }

        authHeader = temporaryHeader;
        authenticatedEmail = email.trim();

        showToast(`Signed in as ${authenticatedEmail}`);
        return true;
    } catch (error) {
        authHeader = null;
        authenticatedEmail = null;
        showToast(`Login failed: ${error.message}`, true);
        return false;
    }
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#039;"
    })[char]);
}

async function loadEvents() {
    eventSelect.innerHTML = '<option value="">Loading events…</option>';

    try {
        const events = await apiRequest(`${API}/events`);

        eventSelect.innerHTML =
            '<option value="">Select an event</option>';

        if (!Array.isArray(events) || events.length === 0) {
            eventSelect.innerHTML =
                '<option value="">No events available</option>';
            return;
        }

        events.forEach((event) => {
            const option = document.createElement("option");
            option.value = event.id;
            option.textContent = event.name;
            option.dataset.description = event.description || "";
            eventSelect.appendChild(option);
        });
    } catch (error) {
        eventSelect.innerHTML =
            '<option value="">Could not load events</option>';
        showToast(`Could not load events: ${error.message}`, true);
    }
}

function updateEventInfo() {
    const option = eventSelect.selectedOptions[0];

    if (!option || !option.value) {
        eventInfo.hidden = true;
        return;
    }

    const description =
        option.dataset.description || "No description available.";

    eventInfo.textContent = `${option.textContent} · ${description}`;
    eventInfo.hidden = false;
}

function resetSelection() {
    selectedSeatId = null;
    bookButton.disabled = true;
    selectionText.textContent = "No seat selected";
}

function renderSeats() {
    seatGrid.replaceChildren();
    resetSelection();

    currentSeats.forEach((seat) => {
        const button = document.createElement("button");
        const isAvailable = seat.status === "AVAILABLE";

        button.type = "button";
        button.className =
            `seat ${isAvailable ? "available" : "booked"}`;

        button.textContent =
            seat.seatNumber || `S${seat.seatId ?? seat.id}`;

        button.title = isAvailable
            ? `Select seat ${button.textContent}`
            : `Seat ${button.textContent} is booked`;

        button.setAttribute("aria-label", button.title);
        button.disabled = !isAvailable;

        button.addEventListener("click", () => {
            selectedSeatId = seat.id;

            seatGrid.querySelectorAll(".seat").forEach((item) => {
                item.classList.remove("selected");
            });

            button.classList.add("selected");
            bookButton.disabled = false;
            selectionText.textContent =
                `Seat ${button.textContent} selected`;
        });

        seatGrid.appendChild(button);
    });
}

async function loadSeats() {
    const showId = showIdInput.value.trim();

    if (!showId || !Number.isInteger(Number(showId)) ||
        Number(showId) < 1) {
        showToast("Enter a valid show ID.", true);
        showIdInput.focus();
        return;
    }

    loadSeatsButton.disabled = true;
    loadSeatsButton.textContent = "Loading…";
    seatArea.hidden = true;

    try {
        const seats = await apiRequest(
            `${API}/shows/${encodeURIComponent(showId)}/seats`
        );

        if (!Array.isArray(seats)) {
            throw new Error("The server returned an unexpected seat list.");
        }

        currentSeats = seats;
        renderSeats();
        seatArea.hidden = false;

        showToast(
            seats.length
                ? `${seats.length} seats loaded.`
                : "No seats were found for this show."
        );
    } catch (error) {
        showToast(`Could not load seats: ${error.message}`, true);
    } finally {
        loadSeatsButton.disabled = false;
        loadSeatsButton.innerHTML =
            'Find seats <span aria-hidden="true">↗</span>';
    }
}

async function bookSelectedSeat() {
    if (!selectedSeatId) {
        showToast("Select an available seat first.", true);
        return;
    }

    if (!(await ensureAuthenticated())) {
        return;
    }

    bookButton.disabled = true;
    bookButton.textContent = "Reserving…";

    try {
        const result = await apiRequest(`${API}/bookings`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                showSeatId: selectedSeatId
            })
        });

        const bookingId = result.bookingId;

        if (bookingId != null) {
            bookingIdInput.value = bookingId;
            showToast(
                `Booking confirmed. Booking ID: ${bookingId}`
            );
        } else {
            showToast(
                result.message || "Booking completed."
            );
        }

        await loadSeats();
    } catch (error) {
        showToast(`Booking failed: ${error.message}`, true);

        if (error.status === 401) {
            authHeader = null;
            authenticatedEmail = null;
        }

        await loadSeats();
    } finally {
        bookButton.innerHTML =
            'Confirm booking <span aria-hidden="true">↗</span>';
        bookButton.disabled = true;
    }
}

async function cancelBooking() {
    const bookingId = bookingIdInput.value.trim();

    if (!bookingId || !Number.isInteger(Number(bookingId)) ||
        Number(bookingId) < 1) {
        showToast("Enter a valid booking ID.", true);
        bookingIdInput.focus();
        return;
    }

    if (!(await ensureAuthenticated())) {
        return;
    }

    cancelButton.disabled = true;
    cancelButton.textContent = "Cancelling…";

    try {
        const result = await apiRequest(
            `${API}/bookings/${encodeURIComponent(bookingId)}/cancel`,
            { method: "PUT" }
        );

        showToast(
            `Booking ${result.id ?? bookingId} cancelled successfully.`
        );

        if (!seatArea.hidden && showIdInput.value.trim()) {
            await loadSeats();
        }
    } catch (error) {
        showToast(`Cancellation failed: ${error.message}`, true);

        if (error.status === 401) {
            authHeader = null;
            authenticatedEmail = null;
        }
    } finally {
        cancelButton.disabled = false;
        cancelButton.textContent = "Cancel booking";
    }
}

function setTheme(theme) {
    const isLight = theme === "light";

    document.body.classList.toggle("light", isLight);
    $("themeIcon").textContent = isLight ? "☾" : "☼";
    $("themeLabel").textContent = isLight ? "Dark mode" : "Light mode";
    document.documentElement.style.colorScheme =
        isLight ? "light" : "dark";

    try {
        localStorage.setItem("core-theme", theme);
    } catch {
        // Theme switching still works without browser storage.
    }
}

function initialiseTheme() {
    let theme = "dark";

    try {
        theme = localStorage.getItem("core-theme") || "dark";
    } catch {
        // Use dark mode by default.
    }

    setTheme(theme);
}

function setupNavigation() {
    document.querySelectorAll("[data-section]").forEach((button) => {
        button.addEventListener("click", () => {
            document.querySelectorAll(".nav-item[data-section]")
                .forEach((item) => item.classList.remove("active"));

            button.classList.add("active");

            const target = button.dataset.section;

            if (target === "reserve") {
                $("reserveSection").scrollIntoView({
                    behavior: "smooth"
                });
            } else if (target === "events") {
                eventSelect.focus();
                $("reserveSection").scrollIntoView({
                    behavior: "smooth"
                });
            } else if (target === "manage") {
                $("manageSection").scrollIntoView({
                    behavior: "smooth"
                });
                bookingIdInput.focus({ preventScroll: true });
            }
        });
    });
}

eventSelect.addEventListener("change", updateEventInfo);
loadSeatsButton.addEventListener("click", loadSeats);
bookButton.addEventListener("click", bookSelectedSeat);
cancelButton.addEventListener("click", cancelBooking);

$("themeToggle").addEventListener("click", () => {
    const nextTheme = document.body.classList.contains("light")
        ? "dark"
        : "light";

    setTheme(nextTheme);
});

showIdInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        loadSeats();
    }
});

initialiseTheme();
setupNavigation();
loadEvents();
