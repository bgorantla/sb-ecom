import { FaBoxOpen, FaHome, FaShoppingCart, FaStore, FaThList } from "react-icons/fa";
import { bannerImageOne, bannerImageThree, bannerImageTwo } from "./constant";

// Each slide's "Shop" button opens its category, so these names must match categories in the store
export const bannerLists = [
    {
        id: 1,
        image: bannerImageThree,
        title: "Home Comfort",
        subtitle: "Home & Living",
        description: "Upgrade your space with cozy, stylish furniture and decor",
        category: "Home & Living",
    },
    {
        id: 2,
        image: bannerImageTwo,
        title: "Latest Tech",
        subtitle: "Electronics",
        description: "Phones, laptops and gadgets for every day",
        category: "Electronics",
    },
    {
        id: 3,
        image: bannerImageOne,
        title: "Fresh Styles",
        subtitle: "Fashion",
        description: "Bright, comfortable looks for every day",
        category: "Fashion",
    },
];


export const adminNavigation = [
  {
    name: "Dashboard", 
    href: "/admin", 
    icon: FaHome, 
    current: true 
  }, {
    name: "Orders", 
    href: "/admin/orders", 
    icon: FaShoppingCart
  }, {
    name: "Products", 
    href: "/admin/products", 
    icon: FaBoxOpen
  }, {
    name: "Categories", 
    href: "/admin/categories", 
    icon: FaThList
  }, {
    name: "Sellers", 
    href: "/admin/sellers", 
    icon: FaStore 
  }
];


export const sellerNavigation = [
  {
    name: "Orders", 
    href: "/admin/orders", 
    icon: FaShoppingCart,
    current: true 
  }, {
    name: "Products", 
    href: "/admin/products", 
    icon: FaBoxOpen
  }
];