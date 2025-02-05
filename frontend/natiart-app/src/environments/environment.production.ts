export const environment = {
  production: true,
  api: {
    directory: {
      url: 'https://natiart.samuelpetre.com/server/directory',
      endpoints: {
        login: '/login',
        logout: '/logout',
        current: '/current',
        user: '/users',
      }
    },
    product: {
      url: 'https://natiart.samuelpetre.com/server/product',
      endpoints: {
        directory: '/categories',
        packages: '/packages',
        products: '/products',
      }
    },
  }
};
