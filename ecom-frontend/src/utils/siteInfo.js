// One place for the details shown on the About page, the Contact page and the footer.
// Leave a value as "" to hide it from the site.
export const siteInfo = {
    storeName: "E-Shop",
    ownerName: "Bhargav Gorantla",
    githubProfileUrl: "https://github.com/bgorantla",
    repoUrl: "https://github.com/bgorantla/sb-ecom",
    linkedinUrl: "", // e.g. "https://www.linkedin.com/in/bhargav-gorantla-44a654207/"
    email: "",       // shown publicly if set, so think about spam before adding it
    courseUrl: "https://github.com/EmbarkXOfficial/spring-boot-course",
};

// Swagger UI is served by the backend. In production that's the same address as
// the site; in local development it's whatever VITE_BACK_END_URL points to.
export const apiDocsUrl = `${import.meta.env.VITE_BACK_END_URL ?? ""}/swagger-ui/index.html`;
