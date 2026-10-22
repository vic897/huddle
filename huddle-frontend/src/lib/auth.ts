export const setToken = (token: string) => {
  if (typeof window !== "undefined") {
    localStorage.setItem("huddle_token", token);
  }
};

export const setInstitutionStatus = (status: string) => {
  if (typeof window !== "undefined") {
    localStorage.setItem("huddle_institution_status", status);
  }
};

export const getInstitutionStatus = () => {
  if (typeof window !== "undefined") {
    return localStorage.getItem("huddle_institution_status");
  }
  return null;
};

export const getToken = () => {
  if (typeof window !== "undefined") {
    return localStorage.getItem("huddle_token");
  }
  return null;
};

export const logout = () => {
  if (typeof window !== "undefined") {
    localStorage.removeItem("huddle_token");
    localStorage.removeItem("huddle_institution_status");
    window.location.href = "/login";
  }
};

export const isLoggedIn = () => {
  return !!getToken();
};
